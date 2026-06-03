package com.speakfit.backend.domain.guide.service;

import com.speakfit.backend.domain.guide.dto.req.CreateGuideReq;
import com.speakfit.backend.domain.guide.dto.req.RecommendStyleReq;
import com.speakfit.backend.domain.guide.dto.res.CreateGuideRes;
import com.speakfit.backend.domain.guide.dto.res.RecommendStyleRes;
import com.speakfit.backend.domain.guide.exception.GuideErrorCode;
import com.speakfit.backend.domain.guide.repository.SpeechStyleClusterRepository;
import com.speakfit.backend.domain.guide.entity.SpeechStyleCluster;

import com.speakfit.backend.domain.style.entity.SpeechStyleMatrix;
import com.speakfit.backend.domain.style.repository.SpeechStyleMatrixRepository;
import com.speakfit.backend.domain.metric.entity.TargetAudienceMetric;
import com.speakfit.backend.domain.metric.repository.TargetAudienceMetricRepository;

import com.speakfit.backend.domain.practice.enums.AudienceUnderstanding;
import com.speakfit.backend.domain.practice.enums.AudienceType;
import com.speakfit.backend.domain.style.enums.StyleType;

import com.speakfit.backend.domain.voice.entity.BaselineVoice;
import com.speakfit.backend.domain.voice.entity.BaselineRegionalMetric;
import com.speakfit.backend.domain.voice.repository.BaselineVoiceRepository;
import com.speakfit.backend.domain.voice.repository.BaselineRegionalMetricRepository;

import com.speakfit.backend.domain.user.entity.User;
import com.speakfit.backend.domain.user.repository.UserRepository;
import com.speakfit.backend.global.apiPayload.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GuideServiceImpl implements GuideService {

    // ── §6 Gold Standard 상수 ──────────────────────────────────────────────
    private static final double GS_TARGET_DB          = 63.72;   // dB SPL
    private static final double GS_TARGET_PAUSE_RATIO = 27.15;   // %
    private static final double GS_ZCR_MEAN           = 0.1108;
    private static final double GS_ZCR_STD            = 0.1895;

    // ── §7 임계값 가드레일 ─────────────────────────────────────────────────
    private static final double WPM_GUARD_MIN          = 100.0;
    private static final double WPM_GUARD_MAX          = 180.0;
    private static final double PITCH_GUARD_RATIO_MIN  = 0.80;   // Baseline -20%
    private static final double PITCH_GUARD_RATIO_MAX  = 1.20;   // Baseline +20%
    // 사람 발화 F0 물리 음역. 이 범위를 벗어난 baseline Pitch는 손상값으로 보고 폴백.
    private static final double MIN_HUMAN_PITCH_HZ     = 65.0;
    private static final double MAX_HUMAN_PITCH_HZ     = 450.0;
    private static final double DB_GUARD_DELTA_MAX     = 6.0;    // ±6 dB
    private static final double PAUSE_GUARD_MIN        = 8.0;    // %
    private static final double PAUSE_GUARD_MAX        = 25.0;   // %

    // ── §3 클러스터 정규화 범위 (matchingRate 절대 적합도용) ───────────────
    private static final double MATCH_NORM_WPM   = 40.0;   // §3 WPM 분산 기준
    private static final double MATCH_NORM_PITCH = 200.0;  // §3 Pitch 분산 기준

    private final SpeechStyleClusterRepository speechStyleClusterRepository;
    private final BaselineVoiceRepository baselineVoiceRepository;
    private final BaselineRegionalMetricRepository baselineRegionalMetricRepository;
    private final UserRepository userRepository;
    private final TargetAudienceMetricRepository targetAudienceMetricRepository;
    private final SpeechStyleMatrixRepository speechStyleMatrixRepository;

    @Override
    @Transactional
    public RecommendStyleRes recommendSpeechStyles(RecommendStyleReq req, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(GuideErrorCode.GUIDE_USER_NOT_FOUND));

        /* 🌟 [STEP 2 리뷰 보완] 개인 BaselineVoice 우선 사용, 없으면 §4 지역 fallback (예외 대신 graceful degradation) */
        Optional<BaselineVoice> baselineOpt = baselineVoiceRepository.findFirstByUserIdAndIsActiveTrueOrderByIdDesc(user.getId());

        Long baselineVoiceId = baselineOpt.map(BaselineVoice::getId).orElse(null);

        // 개인 측정값 우선, null이면 지역 fallback, BaselineVoice 자체가 없으면 지역 fallback
        double basePitch = baselineOpt
                .map(b -> b.getAvgPitch() != null ? b.getAvgPitch() : getRegionalFallbackPitch(user))
                .orElseGet(() -> getRegionalFallbackPitch(user));
        double baseWpm = baselineOpt
                .map(b -> b.getAvgWpm() != null ? b.getAvgWpm() : getRegionalFallbackWpm(user))
                .orElseGet(() -> getRegionalFallbackWpm(user));

        /* 🌟 잘못된 파라미터 유입 시 500 에러 추락 방지를 위한 ENUM 파싱 트라이 캐치 쉴드 */
        TargetAudienceMetric audienceMetric;
        try {
            audienceMetric = targetAudienceMetricRepository
                    .findByAudienceUnderstandingAndAudienceType(
                            AudienceUnderstanding.valueOf(req.getAudienceUnderstanding()),
                            AudienceType.valueOf(req.getAudienceAgeGroup())
                    ).orElse(null);
        } catch (IllegalArgumentException e) {
            throw new CustomException(GuideErrorCode.CLUSTER_MATRIX_NOT_FOUND);
        }

        // [STEP 3-D] §8 audienceMetric → 표시용 범위 문자열 (클램핑은 createSpeechGuide에서 적용)
        String targetWpmRange = audienceMetric != null
                ? String.format("%.0f - %.0f", audienceMetric.getMinWpm(), audienceMetric.getMaxWpm())
                : String.format("%.0f - %.0f", WPM_GUARD_MIN, WPM_GUARD_MAX);
        String targetPitchVariance = audienceMetric != null
                ? String.format("±%.0f ~ %.0f%%", Math.abs(audienceMetric.getMinPitchRatio() * 100), audienceMetric.getMaxPitchRatio() * 100)
                : String.format("±%.0f ~ %.0f%%", (1 - PITCH_GUARD_RATIO_MIN) * 100, (PITCH_GUARD_RATIO_MAX - 1) * 100);
        String targetPauseRatioRange = audienceMetric != null
                ? String.format("%.0f - %.0f%%", audienceMetric.getMinPauseRatio(), audienceMetric.getMaxPauseRatio())
                : String.format("%.0f - %.0f%%", PAUSE_GUARD_MIN, PAUSE_GUARD_MAX);

        // [STEP 3-B] §3 클러스터 필터링 (지역+성별 기준)
        String userRegionKorean = dialectToKorean(user.getDialect().name());
        String userGenderKorean = "FEMALE".equals(user.getGender().name()) ? "여성" : "남성";
        String dbTargetGroup = userRegionKorean + "_" + userGenderKorean;

        List<SpeechStyleCluster> allClusters = speechStyleClusterRepository.findAll();
        List<SpeechStyleCluster> filteredClusters = allClusters.stream()
                .filter(c -> c.getRegion() != null && c.getRegion().trim().equals(dbTargetGroup))
                .collect(Collectors.toList());

        if (filteredClusters.isEmpty()) {
            filteredClusters = allClusters;
        }

        // [STEP 3-B] matchingRate: §5 ratio로 유저 baseline 투영 후 정규화 유클리드 절대 적합도
        // 투영값(projWpm, projPitch)이 클러스터 중심점에 가까울수록 해당 스타일 적합도 높음
        List<RecommendStyleRes.RecommendedStyle> styleList = new ArrayList<>();

        for (SpeechStyleCluster cluster : filteredClusters) {
            StyleType styleType = StyleType.fromKoreanLabel(cluster.getStyleName());
            double projWpm;
            double projPitch;

            SpeechStyleMatrix matrix = null;
            if (styleType != null) {
                matrix = speechStyleMatrixRepository
                        .findByDialectAndGenderAndStyleType(user.getDialect(), user.getGender(), styleType)
                        .orElse(null);
            }
            if (matrix != null) {
                projWpm   = baseWpm   * matrix.getWpmRatio();
                projPitch = basePitch * matrix.getPitchRatio();
            } else {
                // §5 매트릭스 없으면 §7 guard mid-point를 WPM으로, pitch는 baseline 그대로
                projWpm   = (WPM_GUARD_MIN + WPM_GUARD_MAX) / 2.0;
                projPitch = basePitch;
            }

            // 정규화 유클리드 거리 → 절대 적합도 [0, 100]
            double normDist = Math.sqrt(
                    Math.pow((projWpm   - cluster.getBaseWpm())   / MATCH_NORM_WPM,   2) +
                    Math.pow((projPitch - cluster.getBasePitch()) / MATCH_NORM_PITCH, 2)
            );
            double matchingRate = Math.max(0.0,
                    Math.round((1.0 - normDist / Math.sqrt(2)) * 100.0 * 10.0) / 10.0);

            styleList.add(RecommendStyleRes.RecommendedStyle.builder()
                    .styleName(cluster.getStyleName())
                    .matchingRate(matchingRate)
                    .isBest(false)
                    .description(String.format(
                            "%s 스타일 기준 투영 적합도 %.1f%% — %s 분석 지표 기반 맞춤 가이드라인.",
                            cluster.getStyleName(), matchingRate, cluster.getStyleName()))
                    .build());
        }

        styleList.sort((s1, s2) -> Double.compare(s2.getMatchingRate(), s1.getMatchingRate()));

        if (!styleList.isEmpty()) {
            RecommendStyleRes.RecommendedStyle best = styleList.getFirst();
            styleList.set(0, RecommendStyleRes.RecommendedStyle.builder()
                    .styleName(best.getStyleName())
                    .matchingRate(best.getMatchingRate())
                    .isBest(true)
                    .description(String.format("선택한 스타일이 %s님의 고유 음색 및 설정된 청중 환경에 가장 적합한 모델로 매칭되었습니다.", user.getNickname()))
                    .build());
        }

        return RecommendStyleRes.builder()
                .baselineVoiceId(baselineVoiceId)
                .audienceInfo(RecommendStyleRes.AudienceInfo.builder()
                        .targetWpmRange(targetWpmRange)
                        .targetPitchVariance(targetPitchVariance)
                        .targetPauseRatio(targetPauseRatioRange)
                        .build())
                .recommendedStyles(styleList)
                .build();
    }

    @Override
    @Transactional
    public CreateGuideRes createSpeechGuide(CreateGuideReq req, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(GuideErrorCode.GUIDE_USER_NOT_FOUND));

        BaselineVoice baseline = baselineVoiceRepository.findById(req.getBaselineVoiceId())
                .orElseThrow(() -> new CustomException(GuideErrorCode.BASE_RECORD_NOT_FOUND));

        /* 🌟 [보안 보강] 타인 오디오 ID 조회를 막기 위한 소유권 검증 (IDOR 방어벽) */
        if (!baseline.getUser().getId().equals(userId)) {
            throw new CustomException(GuideErrorCode.BASE_RECORD_NOT_FOUND);
        }

        // [STEP 2-D] 베이스라인 null 시 §4 지역별 기준값 fallback
        //   [가드] baseline Pitch가 사람 음역(65~450Hz) 밖이면 손상값(예: 구버전
        //   piptrack 배음 오인식 3185Hz)으로 보고 지역 기준값으로 폴백한다.
        Double rawBasePitch = baseline.getAvgPitch();
        double basePitch = (rawBasePitch != null
                && rawBasePitch >= MIN_HUMAN_PITCH_HZ
                && rawBasePitch <= MAX_HUMAN_PITCH_HZ)
                ? rawBasePitch
                : getRegionalFallbackPitch(user);
        double baseWpm = baseline.getAvgWpm() != null
                ? baseline.getAvgWpm()
                : getRegionalFallbackWpm(user);

        // [STEP 3-A] 한글 스타일명 → StyleType enum 매핑 (문자열 매칭 일원화)
        if (req.getSelectedStyle() == null) {
            throw new CustomException(GuideErrorCode.CLUSTER_MATRIX_NOT_FOUND);
        }
        StyleType styleType = StyleType.fromKoreanLabel(req.getSelectedStyle());
        if (styleType == null) {
            throw new CustomException(GuideErrorCode.CLUSTER_MATRIX_NOT_FOUND);
        }

        // [STEP 3-D] §5 보정배율 조회 (지역/성별/스타일 키)
        SpeechStyleMatrix matrix = speechStyleMatrixRepository
                .findByDialectAndGenderAndStyleType(user.getDialect(), user.getGender(), styleType)
                .orElse(null);

        double wpmRatio   = (matrix != null) ? matrix.getWpmRatio()   : 0.35;
        double pitchRatio = (matrix != null) ? matrix.getPitchRatio() : 0.80;

        // 목표치 = Baseline × §5 배율
        double targetWpm   = baseWpm   * wpmRatio;
        double targetPitch = basePitch * pitchRatio;

        // [STEP 3-C] §8 청중 매트릭스 조회 (optional — null이면 §6/§7만 적용)
        TargetAudienceMetric audienceMetric = null;
        if (req.getAudienceUnderstanding() != null && req.getAudienceAgeGroup() != null) {
            try {
                audienceMetric = targetAudienceMetricRepository
                        .findByAudienceUnderstandingAndAudienceType(
                                AudienceUnderstanding.valueOf(req.getAudienceUnderstanding()),
                                AudienceType.valueOf(req.getAudienceAgeGroup())
                        ).orElse(null);
            } catch (IllegalArgumentException ignored) { /* 잘못된 enum → fallback */ }
        }

        // [STEP 3-D] §7 WPM 가드레일 + §8 청중 WPM 클램핑
        double wpmMin = WPM_GUARD_MIN;
        double wpmMax = WPM_GUARD_MAX;
        if (audienceMetric != null) {
            wpmMin = Math.max(wpmMin, audienceMetric.getMinWpm());
            wpmMax = Math.min(wpmMax, audienceMetric.getMaxWpm());
        }
        targetWpm = Math.max(wpmMin, Math.min(wpmMax, targetWpm));

        // [STEP 3-D] §7 Pitch 가드레일 (Baseline ±20%)
        double pitchMin = basePitch * PITCH_GUARD_RATIO_MIN;
        double pitchMax = basePitch * PITCH_GUARD_RATIO_MAX;
        targetPitch = Math.max(pitchMin, Math.min(pitchMax, targetPitch));

        // [STEP 3-D] §6 Gold Standard dB + §8 청중 intensity offset
        double targetDb = GS_TARGET_DB;
        if (audienceMetric != null) {
            double intensityMid = (audienceMetric.getMinIntensityDelta() + audienceMetric.getMaxIntensityDelta()) / 2.0;
            targetDb = Math.max(GS_TARGET_DB - DB_GUARD_DELTA_MAX,
                        Math.min(GS_TARGET_DB + DB_GUARD_DELTA_MAX, GS_TARGET_DB + intensityMid));
        }

        // [STEP 3-D] §6 Gold Standard Pause + §8 청중 pause clamp
        double targetPause = GS_TARGET_PAUSE_RATIO;
        if (audienceMetric != null) {
            targetPause = (audienceMetric.getMinPauseRatio() + audienceMetric.getMaxPauseRatio()) / 2.0;
        }
        targetPause = Math.max(PAUSE_GUARD_MIN, Math.min(PAUSE_GUARD_MAX, targetPause));

        String wpmGuideMsg = String.format(
                "선택한 스타일에 따라 발화 속도를 조정했습니다. %s님의 안정적인 흐름 유지를 위해 %.1f wpm 범위를 유지하십시오.",
                user.getNickname(), targetWpm);
        String pitchGuideMsg = String.format(
                "%s님의 음색 성향 대비 안정화된 고도 튜닝을 적용하여 목표 주파수를 %.1f Hz로 제한 권장합니다.",
                user.getNickname(), targetPitch);
        String dbGuideMsg = String.format(
                "§6 Gold Standard %.2f dB 기준, 청중 설정 오프셋 적용 목표 %.2f dB를 유지하십시오.",
                GS_TARGET_DB, targetDb);
        String pauseGuideMsg = String.format(
                "문장 간 최적의 휴지기 분포 목표 %.1f%%를 유지하십시오. (§7 허용 범위: %.0f~%.0f%%)",
                targetPause, PAUSE_GUARD_MIN, PAUSE_GUARD_MAX);

        // [STEP 3-E] guideId 하드코딩 제거 — Guide 전용 엔티티는 STEP 9에서 구현 예정, 현재는 null
        return CreateGuideRes.builder()
                .guideId(null)
                .practiceRecordId(baseline.getId())
                .selectedStyle(req.getSelectedStyle())
                .finalDeliveryGuides(CreateGuideRes.FinalDeliveryGuides.builder()
                        .wpm(CreateGuideRes.MetricDetail.builder().targetValue(targetWpm).unit("wpm").guideMessage(wpmGuideMsg).build())
                        .pitch(CreateGuideRes.MetricDetail.builder().targetValue(targetPitch).unit("Hz").guideMessage(pitchGuideMsg).build())
                        .db(CreateGuideRes.MetricDetail.builder().targetValue(targetDb).unit("dB").guideMessage(dbGuideMsg).build())
                        .zcr(CreateGuideRes.ZcrDetail.builder().masterMean(GS_ZCR_MEAN).masterStd(GS_ZCR_STD).guideMessage("자음 및 모음 분리도 선명도 기준값 세트 (§6 Gold Standard).").build())
                        .pauseRatio(CreateGuideRes.MetricDetail.builder().targetValue(targetPause).unit("%").guideMessage(pauseGuideMsg).build())
                        .build())
                .build();
    }

    // [STEP 3-B] 방언 enum → speech_style_cluster region 한글 매핑
    private String dialectToKorean(String dialectName) {
        return switch (dialectName) {
            case "STANDARD"    -> "표준어";
            case "GYEONGSANG"  -> "경상도";
            case "JEOLLA"      -> "전라도";
            case "GANGWON"     -> "강원도";
            case "CHUNGCHEONG" -> "충청도";
            default            -> "표준어";
        };
    }

    // [STEP 2-D] §4 지역별 기준 pitch fallback — gender+dialect 순으로 조회, 없으면 gender 단독, 최후 고정값
    private double getRegionalFallbackPitch(User user) {
        String gender = user.getGender() != null ? user.getGender().name() : null;
        String dialect = user.getDialect() != null ? user.getDialect().name() : null;
        if (gender != null && dialect != null) {
            return baselineRegionalMetricRepository.findByGenderAndDialect(gender, dialect)
                    .map(BaselineRegionalMetric::getAvgPitch)
                    .orElseGet(() -> baselineRegionalMetricRepository.findByGender(gender)
                            .map(BaselineRegionalMetric::getAvgPitch)
                            .orElse("FEMALE".equals(gender) ? 220.0 : 130.0));
        }
        return 150.0; // gender 자체가 null인 엣지케이스
    }

    // [STEP 2-D] §4 지역별 기준 wpm fallback
    private double getRegionalFallbackWpm(User user) {
        String gender = user.getGender() != null ? user.getGender().name() : null;
        String dialect = user.getDialect() != null ? user.getDialect().name() : null;
        if (gender != null && dialect != null) {
            return baselineRegionalMetricRepository.findByGenderAndDialect(gender, dialect)
                    .map(BaselineRegionalMetric::getAvgWpm)
                    .orElseGet(() -> baselineRegionalMetricRepository.findByGender(gender)
                            .map(BaselineRegionalMetric::getAvgWpm)
                            .orElse("FEMALE".equals(gender) ? 330.0 : 310.0));
        }
        return 310.0; // gender 자체가 null인 엣지케이스
    }
}
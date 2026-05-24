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
import com.speakfit.backend.domain.voice.repository.BaselineVoiceRepository;

import com.speakfit.backend.domain.user.entity.User;
import com.speakfit.backend.domain.user.repository.UserRepository;
import com.speakfit.backend.global.apiPayload.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GuideServiceImpl implements GuideService {

    private final SpeechStyleClusterRepository speechStyleClusterRepository;
    private final BaselineVoiceRepository baselineVoiceRepository;
    private final UserRepository userRepository;
    private final TargetAudienceMetricRepository targetAudienceMetricRepository;
    private final SpeechStyleMatrixRepository speechStyleMatrixRepository;

    @Override
    @Transactional
    public RecommendStyleRes recommendSpeechStyles(RecommendStyleReq req, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(GuideErrorCode.GUIDE_USER_NOT_FOUND));

        /* 🌟 코드래빗 권장사항: 단건조회 findByUserId 대신 이력 방어를 위해 최신 활성화된 베이스라인 명시적 매싱 */
        BaselineVoice baseline = baselineVoiceRepository.findFirstByUserIdAndIsActiveTrueOrderByIdDesc(user.getId())
                .orElseThrow(() -> new CustomException(GuideErrorCode.BASE_RECORD_NOT_FOUND));

        double basePitch = baseline.getAvgPitch() != null ? baseline.getAvgPitch() : 150.0;
        double baseWpm = baseline.getAvgWpm() != null ? baseline.getAvgWpm() : 130.0;

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

        String targetWpmRange = "120 - 150";
        String targetPitchVariance = "±5 ~ 12%";
        String targetPauseRatio = "8 - 16%";
        double pitchModifier = 1.0;
        double wpmModifier = 0.0;

        if (audienceMetric != null) {
            targetWpmRange = String.format("%.0f - %.0f", audienceMetric.getMinWpm(), audienceMetric.getMaxWpm());
            targetPitchVariance = String.format("±%.0f ~ %.0f%%", Math.abs(audienceMetric.getMinPitchRatio()), audienceMetric.getMaxPitchRatio());
            targetPauseRatio = String.format("%.0f - %.0f%%", audienceMetric.getMinPauseRatio(), audienceMetric.getMaxPauseRatio());

            if ("LOW".equals(req.getAudienceUnderstanding()) && "SENIOR".equals(req.getAudienceAgeGroup())) {
                pitchModifier = 1.05;
                wpmModifier = -20.0;
            }
        }

        double userTargetPitch = basePitch * pitchModifier;
        double userTargetWpm = baseWpm + wpmModifier;

        String userRegionKorean = "STANDARD".equals(user.getDialect().name()) ? "표준어" :
                "GYEONGSANG".equals(user.getDialect().name()) ? "경상도" :
                        "JEOLLA".equals(user.getDialect().name()) ? "전라도" :
                                "GANGWON".equals(user.getDialect().name()) ? "강원도" : "충청도";
        String userGenderKorean = "FEMALE".equals(user.getGender().name()) ? "여성" : "남성";
        String dbTargetGroup = userRegionKorean + "_" + userGenderKorean;

        List<SpeechStyleCluster> allClusters = speechStyleClusterRepository.findAll();
        List<SpeechStyleCluster> filteredClusters = allClusters.stream()
                .filter(c -> c.getRegion() != null && c.getRegion().trim().equals(dbTargetGroup))
                .collect(Collectors.toList());

        if (filteredClusters.isEmpty()) {
            filteredClusters = allClusters;
        }

        List<RecommendStyleRes.RecommendedStyle> styleList = new ArrayList<>();
        Map<SpeechStyleCluster, Double> distanceMap = new HashMap<>();

        for (SpeechStyleCluster cluster : filteredClusters) {
            double distance = Math.sqrt(
                    Math.pow(userTargetPitch - cluster.getBasePitch(), 2) +
                            Math.pow(userTargetWpm - cluster.getBaseWpm(), 2)
            );
            distanceMap.put(cluster, distance);
        }

        double maxDistance = distanceMap.values().stream().mapToDouble(v -> v).max().orElse(1.0);
        if (maxDistance == 0) maxDistance = 1.0;

        for (SpeechStyleCluster cluster : filteredClusters) {
            double dist = distanceMap.get(cluster);
            double matchingRate = Math.round((1.0 - (dist / (maxDistance * 1.5))) * 100.0 * 10.0) / 10.0;
            matchingRate = Math.max(0.0, Math.min(100.0, matchingRate));

            styleList.add(RecommendStyleRes.RecommendedStyle.builder()
                    .styleName(cluster.getStyleName())
                    .matchingRate(matchingRate)
                    .isBest(false)
                    .description(String.format("%s 분석 지표 기반 맞춤형 가이드라인 스펙을 서빙합니다.", cluster.getStyleName()))
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
                .baselineVoiceId(baseline.getId())
                .audienceInfo(RecommendStyleRes.AudienceInfo.builder()
                        .targetWpmRange(targetWpmRange)
                        .targetPitchVariance(targetPitchVariance)
                        .targetPauseRatio(targetPauseRatio)
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

        double basePitch = baseline.getAvgPitch() != null ? baseline.getAvgPitch() : 150.0;
        double baseWpm = baseline.getAvgWpm() != null ? baseline.getAvgWpm() : 130.0;

        /* 🌟 [오류 수정] 지현이가 선택한 '지적인 스타일' 분기 누락 해결 및 비정상 스타일 인입 방어 */
        String styleTypeStr;
        if (req.getSelectedStyle() == null) {
            throw new CustomException(GuideErrorCode.CLUSTER_MATRIX_NOT_FOUND);
        }

        if (req.getSelectedStyle().contains("열정적인")) styleTypeStr = "ENERGETIC_FAST";
        else if (req.getSelectedStyle().contains("전달력 있는")) styleTypeStr = "DELIVERY";
        else if (req.getSelectedStyle().contains("신중한")) styleTypeStr = "CALM_LOW_TONE";
        else if (req.getSelectedStyle().contains("지적인")) styleTypeStr = "STANDARD_LECTURE"; // 명시적 싱크업 추가
        else throw new CustomException(GuideErrorCode.CLUSTER_MATRIX_NOT_FOUND);

        SpeechStyleMatrix matrix = speechStyleMatrixRepository
                .findByDialectAndGenderAndStyleType(
                        user.getDialect(),
                        user.getGender(),
                        StyleType.valueOf(styleTypeStr)
                )
                .orElse(null);

        double wpmRatio = (matrix != null) ? matrix.getWpmRatio() : 0.35;
        double pitchRatio = (matrix != null) ? matrix.getPitchRatio() : 0.80;

        double targetWpm = baseWpm * wpmRatio;
        double targetPitch = basePitch * pitchRatio;
        double targetDb = 63.72;

        if (targetWpm < 100) targetWpm = 100;
        if (targetWpm > 180) targetWpm = 180;

        double pitchMin = basePitch * 0.8;
        double pitchMax = basePitch * 1.2;
        if (targetPitch < pitchMin) targetPitch = pitchMin;
        if (targetPitch > pitchMax) targetPitch = pitchMax;

        String wpmGuideMsg = String.format("선택한 스타일에 따라 발화 속도를 조정했습니다. %s님의 안정적인 흐름 유지를 위해 %.1f wpm 범위를 유지하십시오.", user.getNickname(), targetWpm);
        String pitchGuideMsg = String.format("%s님의 음색 성향 대비 안정화된 고도 튜닝을 적용하여 목표 주파수를 %.1f Hz로 제한 권장합니다.", user.getNickname(), targetPitch);

        return CreateGuideRes.builder()
                .guideId(1L)
                .practiceRecordId(baseline.getId())
                .selectedStyle(req.getSelectedStyle())
                .finalDeliveryGuides(CreateGuideRes.FinalDeliveryGuides.builder()
                        .wpm(CreateGuideRes.MetricDetail.builder().targetValue(targetWpm).unit("wpm").guideMessage(wpmGuideMsg).build())
                        .pitch(CreateGuideRes.MetricDetail.builder().targetValue(targetPitch).unit("Hz").guideMessage(pitchGuideMsg).build())
                        .db(CreateGuideRes.MetricDetail.builder().targetValue(targetDb).unit("dB").guideMessage("마스터 가이드 표준 성량 지표인 63.72 dB 규격을 준수하십시오.").build())
                        .zcr(CreateGuideRes.ZcrDetail.builder().masterMean(0.1108).masterStd(0.1895).guideMessage("자음 및 모음 분리도 선명도 기준값 세트입니다.").build())
                        .pauseRatio(CreateGuideRes.MetricDetail.builder().targetValue(27.15).unit("%").guideMessage("문장 간 최적의 휴지기 분포 규격인 27.15%를 목표로 설정합니다.").build())
                        .build())
                .build();
    }
}
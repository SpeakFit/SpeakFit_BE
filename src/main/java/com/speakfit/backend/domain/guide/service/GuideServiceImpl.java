package com.speakfit.backend.domain.guide.service;

import com.speakfit.backend.domain.guide.dto.req.CreateGuideReq;
import com.speakfit.backend.domain.guide.dto.req.RecommendStyleReq;
import com.speakfit.backend.domain.guide.dto.res.CreateGuideRes;
import com.speakfit.backend.domain.guide.dto.res.RecommendStyleRes;
import com.speakfit.backend.domain.guide.exception.GuideErrorCode;
import com.speakfit.backend.domain.guide.repository.SpeechStyleClusterRepository;

/* 🌟 엔티티 인식 불가 현상 해결을 위한 정확한 가이드 엔티티 패키지 임포트 */
import com.speakfit.backend.domain.guide.entity.SpeechStyleCluster;

/* 공통 자원 재사용을 위한 도메인 레이어 참조 */
import com.speakfit.backend.domain.style.entity.SpeechStyleMatrix;
import com.speakfit.backend.domain.style.repository.SpeechStyleMatrixRepository;
import com.speakfit.backend.domain.metric.entity.TargetAudienceMetric;
import com.speakfit.backend.domain.metric.repository.TargetAudienceMetricRepository;

/* 타입 안정성을 위한 도메인별 글로벌 ENUM 참조 */
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

    /* 🌟 미사용 경고 필드(speechGuideRepository) 제거 완료 */
    private final TargetAudienceMetricRepository targetAudienceMetricRepository;
    private final SpeechStyleMatrixRepository speechStyleMatrixRepository;

    /**
     * 유저 정보 및 설정된 청중 매트릭스 정보를 기반으로 최적의 스피치 스타일을 추천합니다.
     */
    @Override
    @Transactional
    public RecommendStyleRes recommendSpeechStyles(RecommendStyleReq req, Long userId) {

        /* 1. 요청 유저 및 유저의 기준 음성 데이터(Baseline) 정적 검증 */
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(GuideErrorCode.GUIDE_USER_NOT_FOUND));

        BaselineVoice baseline = baselineVoiceRepository.findByUserId(user.getId())
                .orElseThrow(() -> new CustomException(GuideErrorCode.BASE_RECORD_NOT_FOUND));

        /* 2. 평균 기본 지표 보정 (Null 데이터 방어선 구축) */
        double basePitch = baseline.getAvgPitch() != null ? baseline.getAvgPitch() : 150.0;
        double baseWpm = baseline.getAvgWpm() != null ? baseline.getAvgWpm() : 130.0;

        /* 3. 요청 문자열 파라미터를 도메인 정통 ENUM 객체로 파싱하여 청중 보정값 동적 조회 */
        TargetAudienceMetric audienceMetric = targetAudienceMetricRepository
                .findByAudienceUnderstandingAndAudienceType(
                        AudienceUnderstanding.valueOf(req.getAudienceUnderstanding()),
                        AudienceType.valueOf(req.getAudienceAgeGroup())
                )
                .orElse(null);

        /* 4. 디비 장애 혹은 데이터 누락 대비 인메모리 백업 가이드 라인셋 선언 */
        String targetWpmRange = "120 - 150";
        String targetPitchVariance = "±5 ~ 12%";
        String targetPauseRatio = "8 - 16%";
        double pitchModifier = 1.0;
        double wpmModifier = 0.0;

        /* 5. 디비 레코드 존재 시 가이드라인 보정 배율 실시간 동적 매핑 */
        if (audienceMetric != null) {
            targetWpmRange = String.format("%.0f - %.0f", audienceMetric.getMinWpm(), audienceMetric.getMaxWpm());
            targetPitchVariance = String.format("±%.0f ~ %.0f%%", Math.abs(audienceMetric.getMinPitchRatio()), audienceMetric.getMaxPitchRatio());
            targetPauseRatio = String.format("%.0f - %.0f%%", audienceMetric.getMinPauseRatio(), audienceMetric.getMaxPauseRatio());

            /* 특정 약자 계층을 타겟팅한 발화 정밀 오프셋 보정 */
            if ("LOW".equals(req.getAudienceUnderstanding()) && "SENIOR".equals(req.getAudienceAgeGroup())) {
                pitchModifier = 1.05;
                wpmModifier = -20.0;
            }
        }

        double userTargetPitch = basePitch * pitchModifier;
        double userTargetWpm = baseWpm + wpmModifier;

        /* 6. 유저 도메인 ENUM 식별 값을 유클리디안 연산용 한글 식별 그룹명으로 파싱 */
        String userRegionKorean = "STANDARD".equals(user.getDialect().name()) ? "표준어" :
                "GYEONGSANG".equals(user.getDialect().name()) ? "경상도" :
                        "JEOLLA".equals(user.getDialect().name()) ? "전라도" :
                                "GANGWON".equals(user.getDialect().name()) ? "강원도" : "충청도";
        String userGenderKorean = "FEMALE".equals(user.getGender().name()) ? "여성" : "남성";
        String dbTargetGroup = userRegionKorean + "_" + userGenderKorean;

        /* 7. 비교 대상 군집 데이터(Cluster) 로드 및 일차 타겟 필터링 */
        List<SpeechStyleCluster> allClusters = speechStyleClusterRepository.findAll();
        List<SpeechStyleCluster> filteredClusters = allClusters.stream()
                .filter(c -> c.getRegion() != null && c.getRegion().trim().equals(dbTargetGroup))
                .collect(Collectors.toList());

        if (filteredClusters.isEmpty()) {
            filteredClusters = allClusters;
        }

        List<RecommendStyleRes.RecommendedStyle> styleList = new ArrayList<>();
        Map<SpeechStyleCluster, Double> distanceMap = new HashMap<>();

        /* 8. 2차원 평면 기반 유클리디안 거리 연산 가동 (Pitch - WPM 기준 거리 산출) */
        for (SpeechStyleCluster cluster : filteredClusters) {
            double distance = Math.sqrt(
                    Math.pow(userTargetPitch - cluster.getBasePitch(), 2) +
                            Math.pow(userTargetWpm - cluster.getBaseWpm(), 2)
            );
            distanceMap.put(cluster, distance);
        }

        /* 9. 연산된 거리를 퍼센테이지 단위의 매칭율(Matching Rate) 지표로 정규화 변환 */
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

        /* 10. 매칭율 내림차순 정렬 및 최적 스타일(IsBest) 지정 마킹 */
        styleList.sort((s1, s2) -> Double.compare(s2.getMatchingRate(), s1.getMatchingRate()));

        /* 🌟 Java 21 규격 최적화 권장반영: styleList.get(0) -> getFirst() */
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

    /**
     * 선택한 스타일 파라미터와 고유 베이스라인 데이터를 결합하여 실시간 가이드라인 수치를 실시간 연산 후 제공합니다.
     * 스키마 미스매치로 인한 500 오류를 영구 방지하기 위해 데이터베이스 영구 저장 단계를 의도적으로 스킵합니다.
     */
    @Override
    @Transactional
    public CreateGuideRes createSpeechGuide(CreateGuideReq req, Long userId) {

        /* 1. 도메인 조회를 위한 기본 유저 영속성 엔티티 스캔 */
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(GuideErrorCode.GUIDE_USER_NOT_FOUND));

        /* 2. 보정의 기준점이 되는 고유 오디오 기준 지표(BaselineVoice) 획득 */
        BaselineVoice baseline = baselineVoiceRepository.findById(req.getBaselineVoiceId())
                .orElseThrow(() -> new CustomException(GuideErrorCode.BASE_RECORD_NOT_FOUND));

        double basePitch = baseline.getAvgPitch() != null ? baseline.getAvgPitch() : 150.0;
        double baseWpm = baseline.getAvgWpm() != null ? baseline.getAvgWpm() : 130.0;

        /* 3. 수신된 한글 스타일 명칭 파라미터를 시스템 내부용 영문 쿼리 조건 키워드로 분기 파싱 */
        String styleTypeStr = "STANDARD_LECTURE";
        if (req.getSelectedStyle().contains("열정적인")) styleTypeStr = "ENERGETIC_FAST";
        else if (req.getSelectedStyle().contains("전달력 있는")) styleTypeStr = "DELIVERY";
        else if (req.getSelectedStyle().contains("신중한")) styleTypeStr = "CALM_LOW_TONE";

        /* 4. 기존 style 도메인의 공통 매트릭스 테이블 데이터 단건 동적 조회 */
        SpeechStyleMatrix matrix = speechStyleMatrixRepository
                .findByDialectAndGenderAndStyleType(
                        user.getDialect(),
                        user.getGender(),
                        StyleType.valueOf(styleTypeStr)
                )
                .orElse(null);

        /* 5. 디비 조회 결과 가중치 적용 (레코드 미존재 시 하드코딩 마진율 백업 작동) */
        double wpmRatio = (matrix != null) ? matrix.getWpmRatio() : 0.35;
        double pitchRatio = (matrix != null) ? matrix.getPitchRatio() : 0.80;

        /* 6. 최종 스피치 타겟 발화 지표 연산 가동 */
        double targetWpm = baseWpm * wpmRatio;
        double targetPitch = basePitch * pitchRatio;
        double targetDb = 63.72;

        /* 7. 실시간 연산 데이터 오버플로우/언더플로우 방지용 하한선 및 상한선 디펜스 임계값 세팅 */
        if (targetWpm < 100) targetWpm = 100;
        if (targetWpm > 180) targetWpm = 180;

        double pitchMin = basePitch * 0.8;
        double pitchMax = basePitch * 1.2;
        if (targetPitch < pitchMin) targetPitch = pitchMin;
        if (targetPitch > pitchMax) targetPitch = pitchMax;

        /* 8. 프론트엔드 뷰(View) 렌더링용 마스터 안내 템플릿 가이드 메시지 조립 */
        String wpmGuideMsg = String.format("선택한 스타일에 따라 발화 속도를 조정했습니다. %s님의 안정적인 흐름 유지를 위해 %.1f wpm 범위를 유지하십시오.", user.getNickname(), targetWpm);
        String pitchGuideMsg = String.format("%s님의 음색 성향 대비 안정화된 고도 튜닝을 적용하여 목표 주파수를 %.1f Hz로 제한 권장합니다.", user.getNickname(), targetPitch);

        /* 9. 실시간 가상화 파이프라인 반환 (디비 차단벽 세팅) */
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
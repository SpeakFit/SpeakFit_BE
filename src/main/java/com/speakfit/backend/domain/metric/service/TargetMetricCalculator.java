package com.speakfit.backend.domain.metric.service;

import com.speakfit.backend.domain.metric.dto.TargetMetricResult;
import com.speakfit.backend.domain.metric.entity.MasterDeliveryMetric;
import com.speakfit.backend.domain.metric.entity.MetricThreshold;
import com.speakfit.backend.domain.metric.entity.TargetAudienceMetric;
import com.speakfit.backend.domain.metric.enums.MetricType;
import com.speakfit.backend.domain.metric.exception.MetricErrorCode;
import com.speakfit.backend.domain.metric.repository.MasterDeliveryMetricRepository;
import com.speakfit.backend.domain.metric.repository.MetricThresholdRepository;
import com.speakfit.backend.domain.metric.repository.TargetAudienceMetricRepository;
import com.speakfit.backend.domain.practice.enums.AudienceType;
import com.speakfit.backend.domain.practice.enums.AudienceUnderstanding;
import com.speakfit.backend.domain.style.entity.SpeechStyleMatrix;
import com.speakfit.backend.domain.style.enums.StyleType;
import com.speakfit.backend.domain.style.repository.SpeechStyleMatrixRepository;
import com.speakfit.backend.domain.user.entity.User;
import com.speakfit.backend.domain.voice.entity.BaselineVoice;
import com.speakfit.backend.domain.voice.repository.BaselineVoiceRepository;
import com.speakfit.backend.global.apiPayload.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TargetMetricCalculator {

    private final BaselineVoiceRepository baselineVoiceRepository;
    private final SpeechStyleMatrixRepository speechStyleMatrixRepository;
    private final TargetAudienceMetricRepository targetAudienceMetricRepository;
    private final MasterDeliveryMetricRepository masterDeliveryMetricRepository;
    private final MetricThresholdRepository metricThresholdRepository;

    // 사람 목소리 기본주파수(F0)의 물리적 음역. 이 범위를 벗어난 baseline Pitch는
    // 손상된 값(예: 구버전 piptrack 배음 오인식)으로 보고 목표 Pitch 산출을 건너뛴다.
    // 근거: Aalto Univ. Speech Processing — 성인 발화 F0 약 80~450Hz.
    private static final double MIN_HUMAN_PITCH_HZ = 65.0;
    private static final double MAX_HUMAN_PITCH_HZ = 450.0;

    public TargetMetricResult calculate(
            User user,
            StyleType styleType,
            AudienceUnderstanding audienceUnderstanding,
            AudienceType audienceType
    ) {
        BaselineVoice baselineVoice = baselineVoiceRepository
                .findFirstByUserIdAndIsActiveTrueOrderByIdDesc(user.getId())
                .orElse(null);
        SpeechStyleMatrix styleMatrix = speechStyleMatrixRepository
                .findByDialectAndGenderAndStyleType(user.getDialect(), user.getGender(), styleType)
                .orElseThrow(() -> new CustomException(MetricErrorCode.SPEECH_STYLE_MATRIX_NOT_FOUND));
        TargetAudienceMetric audienceMetric = targetAudienceMetricRepository
                .findByAudienceUnderstandingAndAudienceType(audienceUnderstanding, audienceType)
                .orElseThrow(() -> new CustomException(MetricErrorCode.TARGET_AUDIENCE_METRIC_NOT_FOUND));
        MasterDeliveryMetric masterDeliveryMetric = masterDeliveryMetricRepository
                .findFirstByOrderByIdAsc()
                .orElseThrow(() -> new CustomException(MetricErrorCode.MASTER_DELIVERY_METRIC_NOT_FOUND));

        Double targetWpm = calculateTargetWpm(baselineVoice, styleMatrix, audienceMetric);
        Double targetPitch = calculateTargetPitch(baselineVoice, styleMatrix, audienceMetric);
        Double targetIntensity = calculateTargetIntensity(baselineVoice, masterDeliveryMetric, audienceMetric);
        Double targetZcr = calculateTargetZcr(masterDeliveryMetric);
        Double targetPauseRatio = calculateTargetPauseRatio(masterDeliveryMetric, audienceMetric);

        return new TargetMetricResult(
                targetWpm,
                targetPitch,
                targetIntensity,
                targetZcr,
                targetPauseRatio
        );
    }

    private Double calculateTargetWpm(
            BaselineVoice baselineVoice,
            SpeechStyleMatrix styleMatrix,
            TargetAudienceMetric audienceMetric
    ) {
        if (baselineVoice == null || baselineVoice.getAvgWpm() == null || styleMatrix == null) {
            return null;
        }

        Double targetWpm = baselineVoice.getAvgWpm() * styleMatrix.getWpmRatio();
        if (audienceMetric != null) {
            targetWpm = clamp(targetWpm, audienceMetric.getMinWpm(), audienceMetric.getMaxWpm());
        }

        MetricThreshold threshold = findThreshold(MetricType.WPM);
        if (threshold != null) {
            targetWpm = clamp(targetWpm, threshold.getMinValue(), threshold.getMaxValue());
        }

        return round(targetWpm);
    }

    private Double calculateTargetPitch(
            BaselineVoice baselineVoice,
            SpeechStyleMatrix styleMatrix,
            TargetAudienceMetric audienceMetric
    ) {
        if (baselineVoice == null || baselineVoice.getAvgPitch() == null || styleMatrix == null) {
            return null;
        }

        // [가드] baseline Pitch가 사람 음역(65~450Hz)을 벗어나면 손상된 값으로 판단.
        //   ratio 클램프는 baseline 대비 상대값이라 절대 이상치(예: 3185Hz)를 못 거른다.
        //   이 경우 목표 Pitch를 null로 두어 RAG·리포트에서 해당 줄을 생략한다.
        double baselinePitch = baselineVoice.getAvgPitch();
        if (baselinePitch < MIN_HUMAN_PITCH_HZ || baselinePitch > MAX_HUMAN_PITCH_HZ) {
            return null;
        }

        Double targetPitch = baselineVoice.getAvgPitch() * styleMatrix.getPitchRatio();
        if (audienceMetric != null) {
            targetPitch = clampByBaselineRatio(
                    targetPitch,
                    baselineVoice.getAvgPitch(),
                    audienceMetric.getMinPitchRatio(),
                    audienceMetric.getMaxPitchRatio()
            );
        }

        MetricThreshold threshold = findThreshold(MetricType.PITCH_RATIO);
        if (threshold != null) {
            targetPitch = clampByBaselineRatio(
                    targetPitch,
                    baselineVoice.getAvgPitch(),
                    threshold.getMinValue(),
                    threshold.getMaxValue()
            );
        }

        return round(targetPitch);
    }

    private Double calculateTargetIntensity(
            BaselineVoice baselineVoice,
            MasterDeliveryMetric masterDeliveryMetric,
            TargetAudienceMetric audienceMetric
    ) {
        if (masterDeliveryMetric == null || masterDeliveryMetric.getMasterIntensity() == null) {
            return null;
        }

        Double targetIntensity = masterDeliveryMetric.getMasterIntensity();
        if (baselineVoice != null && baselineVoice.getAvgIntensity() != null && audienceMetric != null) {
            targetIntensity = clampByBaselineDelta(
                    targetIntensity,
                    baselineVoice.getAvgIntensity(),
                    audienceMetric.getMinIntensityDelta(),
                    audienceMetric.getMaxIntensityDelta()
            );
        }

        MetricThreshold threshold = findThreshold(MetricType.INTENSITY_DELTA);
        if (baselineVoice != null && baselineVoice.getAvgIntensity() != null && threshold != null) {
            targetIntensity = clampByBaselineDelta(
                    targetIntensity,
                    baselineVoice.getAvgIntensity(),
                    threshold.getMinValue(),
                    threshold.getMaxValue()
            );
        }

        return round(targetIntensity);
    }

    private Double calculateTargetZcr(MasterDeliveryMetric masterDeliveryMetric) {
        if (masterDeliveryMetric == null || masterDeliveryMetric.getMasterZcr() == null) {
            return null;
        }

        Double targetZcr = masterDeliveryMetric.getMasterZcr();
        MetricThreshold threshold = findThreshold(MetricType.ZCR);
        if (threshold != null) {
            targetZcr = clamp(targetZcr, threshold.getMinValue(), threshold.getMaxValue());
        }

        return round(targetZcr);
    }

    private Double calculateTargetPauseRatio(
            MasterDeliveryMetric masterDeliveryMetric,
            TargetAudienceMetric audienceMetric
    ) {
        if (masterDeliveryMetric == null || masterDeliveryMetric.getMasterPauseRatio() == null) {
            return null;
        }

        Double targetPauseRatio = masterDeliveryMetric.getMasterPauseRatio();
        if (audienceMetric != null) {
            targetPauseRatio = clamp(targetPauseRatio, audienceMetric.getMinPauseRatio(), audienceMetric.getMaxPauseRatio());
        }

        MetricThreshold threshold = findThreshold(MetricType.PAUSE_RATIO);
        if (threshold != null) {
            targetPauseRatio = clamp(targetPauseRatio, threshold.getMinValue(), threshold.getMaxValue());
        }

        return round(targetPauseRatio);
    }

    private MetricThreshold findThreshold(MetricType metricType) {
        return metricThresholdRepository.findByMetricType(metricType)
                .orElseThrow(() -> new CustomException(MetricErrorCode.METRIC_THRESHOLD_NOT_FOUND));
    }

    private Double clampByBaselineRatio(Double value, Double baseline, Double minRatio, Double maxRatio) {
        if (baseline == null) {
            return value;
        }

        Double minValue = minRatio != null ? baseline * (1 + minRatio) : null;
        Double maxValue = maxRatio != null ? baseline * (1 + maxRatio) : null;
        return clamp(value, minValue, maxValue);
    }

    private Double clampByBaselineDelta(Double value, Double baseline, Double minDelta, Double maxDelta) {
        if (baseline == null) {
            return value;
        }

        Double minValue = minDelta != null ? baseline + minDelta : null;
        Double maxValue = maxDelta != null ? baseline + maxDelta : null;
        return clamp(value, minValue, maxValue);
    }

    private Double clamp(Double value, Double minValue, Double maxValue) {
        if (value == null) {
            return null;
        }
        if (minValue != null && value < minValue) {
            return minValue;
        }
        if (maxValue != null && value > maxValue) {
            return maxValue;
        }
        return value;
    }

    private Double round(Double value) {
        if (value == null) {
            return null;
        }

        return Math.round(value * 100.0) / 100.0;
    }
}

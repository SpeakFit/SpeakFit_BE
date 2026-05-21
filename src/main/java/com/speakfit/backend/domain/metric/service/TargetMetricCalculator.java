package com.speakfit.backend.domain.metric.service;

import com.speakfit.backend.domain.metric.dto.TargetMetricResult;
import com.speakfit.backend.domain.metric.entity.MasterDeliveryMetric;
import com.speakfit.backend.domain.metric.entity.MetricThreshold;
import com.speakfit.backend.domain.metric.entity.TargetAudienceMetric;
import com.speakfit.backend.domain.metric.enums.MetricType;
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
                .orElse(null);
        TargetAudienceMetric audienceMetric = targetAudienceMetricRepository
                .findByAudienceUnderstandingAndAudienceType(audienceUnderstanding, audienceType)
                .orElse(null);
        MasterDeliveryMetric masterDeliveryMetric = masterDeliveryMetricRepository
                .findFirstByOrderByIdAsc()
                .orElse(null);

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
        return metricThresholdRepository.findByMetricType(metricType).orElse(null);
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

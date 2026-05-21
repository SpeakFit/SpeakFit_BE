package com.speakfit.backend.domain.metric.repository;

import com.speakfit.backend.domain.metric.entity.MetricThreshold;
import com.speakfit.backend.domain.metric.enums.MetricType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MetricThresholdRepository extends JpaRepository<MetricThreshold, Long> {

    Optional<MetricThreshold> findByMetricType(MetricType metricType);
}

package com.speakfit.backend.domain.metric.repository;

import com.speakfit.backend.domain.metric.entity.TargetAudienceMetric;
import com.speakfit.backend.domain.practice.enums.AudienceType;
import com.speakfit.backend.domain.practice.enums.AudienceUnderstanding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TargetAudienceMetricRepository extends JpaRepository<TargetAudienceMetric, Long> {

    Optional<TargetAudienceMetric> findByAudienceUnderstandingAndAudienceType(
            AudienceUnderstanding audienceUnderstanding,
            AudienceType audienceType
    );
}

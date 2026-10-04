package com.speakfit.backend.domain.voice.repository;

import com.speakfit.backend.domain.voice.entity.BaselineRegionalMetric;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * [STEP 2-C] §4 지역별 기준 음성 지표 Repository
 */
public interface BaselineRegionalMetricRepository extends JpaRepository<BaselineRegionalMetric, Long> {

    Optional<BaselineRegionalMetric> findByGenderAndDialect(String gender, String dialect);

    Optional<BaselineRegionalMetric> findByGender(String gender);
}

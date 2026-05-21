package com.speakfit.backend.domain.metric.repository;

import com.speakfit.backend.domain.metric.entity.MasterDeliveryMetric;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MasterDeliveryMetricRepository extends JpaRepository<MasterDeliveryMetric, Long> {

    Optional<MasterDeliveryMetric> findFirstByOrderByIdAsc();
}

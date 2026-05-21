package com.speakfit.backend.domain.metric.entity;

import com.speakfit.backend.domain.metric.enums.MetricType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(
        name = "metric_threshold",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_metric_threshold_type", columnNames = "metric_type")
        }
)
public class MetricThreshold {
    // 계산 결과의 최종 min/max 제한 -> 계산된 목표값이 이상한 값으로 튀지 않는 역할

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "metric_type", nullable = false, length = 50)
    private MetricType metricType;

    @Column(name = "min_value", nullable = false)
    private Double minValue;

    @Column(name = "max_value", nullable = false)
    private Double maxValue;

    @Column(name = "unit", nullable = false, length = 30)
    private String unit;

    @Column(name = "description", nullable = true, length = 500)
    private String description;
}

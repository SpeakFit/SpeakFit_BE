package com.speakfit.backend.domain.voice.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * [STEP 2-C] §4 지역별 기준 음성 지표
 * 사용자 BaselineVoice가 없을 때 GuideServiceImpl의 fallback으로 사용.
 * gender: 'MALE' | 'FEMALE'
 * dialect: 'STANDARD' | 'GYEONGSANG' | 'JEOLLA' | 'CHUNGCHEONG' | 'JEJU'
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Table(
    name = "baseline_regional_metric",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_brm_gender_dialect",
        columnNames = {"gender", "dialect"}
    )
)
public class BaselineRegionalMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String gender;   // 'MALE' | 'FEMALE'

    @Column(nullable = false, length = 30)
    private String dialect;  // 'STANDARD' | 'GYEONGSANG' | 'JEOLLA' | 'CHUNGCHEONG' | 'JEJU'

    @Column(nullable = false)
    private Double avgPitch;  // Hz

    @Column(nullable = false)
    private Double avgWpm;    // 음절/분
}

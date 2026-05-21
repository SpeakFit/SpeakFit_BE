package com.speakfit.backend.domain.style.entity;

import com.speakfit.backend.domain.style.enums.StyleType;
import com.speakfit.backend.domain.user.enums.Dialect;
import com.speakfit.backend.domain.user.enums.Gender;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(
        name = "speech_style_matrix",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_speech_style_matrix_group_style",
                        columnNames = {"dialect", "gender", "style_type"}
                )
        }
)
public class SpeechStyleMatrix {
    // 지역 + 성별 + 스타일별 WPM/Pitch 배율

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "dialect", nullable = false, length = 30)
    private Dialect dialect;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = false, length = 20)
    private Gender gender;

    @Enumerated(EnumType.STRING)
    @Column(name = "style_type", nullable = false, length = 50)
    private StyleType styleType;

    @Column(name = "pitch_ratio", nullable = false)
    private Double pitchRatio;

    @Column(name = "wpm_ratio", nullable = false)
    private Double wpmRatio;
}

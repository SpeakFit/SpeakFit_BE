package com.speakfit.backend.domain.style.entity;

import com.speakfit.backend.domain.style.enums.StyleType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(
        name = "speech_style",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_speech_style_type", columnNames = "style_type")
        }
)
public class SpeechStyle {
    // 스타일 이름/설명/샘플 음성

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "style_type", nullable = false, length = 50)
    private StyleType styleType;

    @Column(name = "display_name", nullable = false, length = 50)
    private String displayName;

    @Column(name = "description", nullable = false, length = 255)
    private String description;

    @Column(name = "sample_audio_url_male", length = 500, nullable = false)
    private String sampleAudioUrlMale;

    @Column(name = "sample_audio_url_female", length = 500, nullable = false)
    private String sampleAudioUrlFemale;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;
}

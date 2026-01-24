package com.speakfit.backend.domain.style.entity;

import com.speakfit.backend.domain.model.entity.SpeechModel;
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
                @UniqueConstraint(name = "uk_speech_style_type", columnNames = "style_type"),
                @UniqueConstraint(name="uk_speech_style_model", columnNames="model_id")
        }
)
public class SpeechStyle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "model_id", nullable = false)
    private SpeechModel speechModel;

    @Enumerated(EnumType.STRING)
    @Column(name = "style_type", nullable = false, length = 50)
    private StyleType styleType;

    @Column(length = 255)
    private String description;
}

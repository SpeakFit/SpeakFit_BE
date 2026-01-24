package com.speakfit.backend.domain.model.entity;

import com.speakfit.backend.domain.user.enums.Dialect;
import com.speakfit.backend.domain.user.enums.Gender;
import com.speakfit.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Table(name = "speech_model")
public class SpeechModel extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Gender gender;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Dialect dialect;

    @Column(nullable = false)
    private double wpm;

    @Column(nullable = false)
    private double pitch;

    @Column(nullable = false)
    private double intensity;

    @Column(name = "pause_ratio", nullable = false)
    private double pauseRatio;

    @Column(nullable = false)
    private double zcr;

}

package com.speakfit.backend.domain.guide.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "speech_style_cluster")
public class SpeechStyleCluster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String region;

    @Column(nullable = false)
    private String gender;

    @Column(nullable = false, name = "style_name")
    private String styleName;

    @Column(nullable = false, name = "base_pitch")
    private Double basePitch;

    @Column(nullable = false, name = "base_wpm")
    private Double baseWpm;
}
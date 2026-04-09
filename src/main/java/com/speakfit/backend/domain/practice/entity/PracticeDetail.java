package com.speakfit.backend.domain.practice.entity;

import com.speakfit.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "practice_detail")
public class PracticeDetail extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "record_id", nullable = false)
    private PracticeRecord practiceRecord;

    @Column(name = "word_index")
    private Integer wordIndex;

    @Column(name = "start_time")
    private Double startTime;

    @Column(name = "end_time")
    private Double endTime;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "status")
    private String status; // NORMAL, FAST, SLOW 등
}

package com.speakfit.backend.domain.practice.entity;

import com.speakfit.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "ai_analysis_result")
public class AiAnalysisResult extends BaseEntity {

    @Id
    @Column(name = "record_id")
    private Long recordId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "record_id")
    private PracticeRecord practiceRecord;

    @Column(name = "ai_summary", columnDefinition = "TEXT")
    private String aiSummary;

    public void updateSummary(String aiSummary) {
        this.aiSummary = aiSummary;
    }
}
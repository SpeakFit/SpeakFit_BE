package com.speakfit.backend.domain.practice.entity;

import com.speakfit.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "practice_issue")
public class PracticeIssue extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "record_id", nullable = false)
    private PracticeRecord practiceRecord;

    @Column(name = "start_index")
    private Integer startIndex;

    @Column(name = "end_index")
    private Integer endIndex;

    @Column(name = "issue_summary", length = 255)
    private String issueSummary;

    @Column(name = "feedback_content", columnDefinition = "TEXT")
    private String feedbackContent;

    @Column(name = "wpm")
    private Double wpm;

    @Column(name = "intensity")
    private Double intensity;
}

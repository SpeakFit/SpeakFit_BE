package com.speakfit.backend.domain.practice.entity;

import com.speakfit.backend.domain.practice.enums.PracticeIssueType;
import com.speakfit.backend.domain.script.entity.ScriptSentence;
import com.speakfit.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "practice_issue", indexes = {
        @Index(name = "idx_practice_issue_record_id", columnList = "record_id"),
        @Index(name = "idx_practice_issue_record_display", columnList = "record_id, display_order")
})
public class PracticeIssue extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "record_id", nullable = false)
    private PracticeRecord practiceRecord;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "script_sentence_id")
    private ScriptSentence scriptSentence;

    @Enumerated(EnumType.STRING)
    @Column(name = "issue_type")
    private PracticeIssueType issueType;

    @Column(name = "sentence_index")
    private Integer sentenceIndex;

    @Column(name = "start_index")
    private Integer startIndex;

    @Column(name = "end_index")
    private Integer endIndex;

    @Column(name = "issue_summary", length = 255)
    private String issueSummary;

    @Column(name = "feedback_content", columnDefinition = "TEXT")
    private String feedbackContent;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "score")
    private Double score;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Column(name = "wpm")
    private Double wpm;

    @Column(name = "intensity")
    private Double intensity;
}

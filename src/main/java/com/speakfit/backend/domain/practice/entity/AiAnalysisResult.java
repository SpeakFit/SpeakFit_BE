package com.speakfit.backend.domain.practice.entity;

import com.speakfit.backend.domain.practice.dto.res.PythonAnalysisRes;
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
    private Long recordId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "record_id")
    private PracticeRecord practiceRecord;

    @Column(name = "ai_summary", columnDefinition = "TEXT")
    private String aiSummary;

    @Column(name = "wpm_summary", length = 255)
    private String wpmSummary;

    @Column(name = "wpm_feedback", columnDefinition = "TEXT")
    private String wpmFeedback;

    @Column(name = "energy_summary", length = 255)
    private String energySummary;

    @Column(name = "energy_feedback", columnDefinition = "TEXT")
    private String energyFeedback;

    @Column(name = "pause_feedback", columnDefinition = "TEXT")
    private String pauseFeedback;

    @Column(name = "symbol_feedback", columnDefinition = "TEXT")
    private String symbolFeedback;

    @Column(name = "goal_similarity_score")
    private Double goalSimilarityScore;

    @Column(name = "goal_summary", length = 255)
    private String goalSummary;

    @Column(name = "goal_feedback", columnDefinition = "TEXT")
    private String goalFeedback;

    // [STAGE 3] 출력 구조 확장 — 강점/핵심 개선 액션/연습 팁
    @Column(name = "strengths", columnDefinition = "TEXT")
    private String strengths;

    @Column(name = "improvements", columnDefinition = "TEXT")
    private String improvements;

    @Column(name = "practice_tip", columnDefinition = "TEXT")
    private String practiceTip;

    // [STAGE 4] 내용·구성 피드백
    @Column(name = "content_summary", length = 255)
    private String contentSummary;

    @Column(name = "content_feedback", columnDefinition = "TEXT")
    private String contentFeedback;

    // 클래스 내부에 데이터 업데이트 메서드 추가
    public void updateAiData(PythonAnalysisRes data) {
        // null이 아닐 때만 업데이트하여 기존 데이터를 보존합니다.
        if (data.getAiSummary() != null) this.aiSummary = data.getAiSummary();
        if (data.getWpmSummary() != null) this.wpmSummary = data.getWpmSummary();
        if (data.getWpmFeedback() != null) this.wpmFeedback = data.getWpmFeedback();
        if (data.getEnergySummary() != null) this.energySummary = data.getEnergySummary();
        if (data.getEnergyFeedback() != null) this.energyFeedback = data.getEnergyFeedback();
        if (data.getPauseFeedback() != null) this.pauseFeedback = data.getPauseFeedback();
        if (data.getSymbolFeedback() != null) this.symbolFeedback = data.getSymbolFeedback();
        if (data.getGoalSimilarityScore() != null) this.goalSimilarityScore = data.getGoalSimilarityScore();
        if (data.getGoalSummary() != null) this.goalSummary = data.getGoalSummary();
        if (data.getGoalFeedback() != null) this.goalFeedback = data.getGoalFeedback();
        if (data.getStrengths() != null) this.strengths = data.getStrengths();
        if (data.getImprovements() != null) this.improvements = data.getImprovements();
        if (data.getPracticeTip() != null) this.practiceTip = data.getPracticeTip();
        if (data.getContentSummary() != null) this.contentSummary = data.getContentSummary();
        if (data.getContentFeedback() != null) this.contentFeedback = data.getContentFeedback();
    }
}

package com.speakfit.backend.domain.feedback.entity;

import com.speakfit.backend.domain.feedback.enums.FeedbackStatus;
import com.speakfit.backend.domain.user.entity.User;
import com.speakfit.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "feedback")
public class Feedback extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private FeedbackStatus status;

    // 스피치 스타일 분석
    private String mostSimilarStyle;
    private Integer matchingRate;
    @Column(columnDefinition = "TEXT")
    private String styleDescription;

    // AI 상세 진단 (AI Report)
    private String positiveTitle;
    @Column(columnDefinition = "TEXT")
    private String positiveDescription;
    private String improvementTitle;
    @Column(columnDefinition = "TEXT")
    private String improvementDescription;

    // 연습 가이드 요약 (Practice Guide)
    private String guideSummary;
    @Column(columnDefinition = "TEXT")
    private String guideNextStep;

    // 비즈니스 로직 (업데이트 메소드)
    // AI 분석 완료 시 데이터를 업데이트하는 메소드
    public void completeAnalysis(String mostSimilarStyle, Integer matchingRate, String styleDescription,
                                 String positiveTitle, String positiveDescription,
                                 String improvementTitle, String improvementDescription,
                                 String guideSummary, String guideNextStep) {
        this.mostSimilarStyle = mostSimilarStyle;
        this.matchingRate = matchingRate;
        this.styleDescription = styleDescription;
        this.positiveTitle = positiveTitle;
        this.positiveDescription = positiveDescription;
        this.improvementTitle = improvementTitle;
        this.improvementDescription = improvementDescription;
        this.guideSummary = guideSummary;
        this.guideNextStep = guideNextStep;
        this.status = FeedbackStatus.COMPLETED; // 분석 완료 상태로 변경]
    }
}
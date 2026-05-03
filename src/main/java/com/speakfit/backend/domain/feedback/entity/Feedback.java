package com.speakfit.backend.domain.feedback.entity;

import com.speakfit.backend.domain.feedback.enums.FeedbackStatus;
import com.speakfit.backend.domain.user.entity.User;
import com.speakfit.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

// 종합 피드백 정보를 관리하는 엔티티 클래스입니다.
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "feedback")
public class Feedback extends BaseEntity {

    // 피드백 ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // 피드백을 요청한 사용자
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 1. 피드백 기간
    // 피드백 시작일
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;
    // 피드백 종료일
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    // 피드백 상태 (GENERATING, COMPLETED, FAILED 등)
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private FeedbackStatus status;

    // 2. 스피치 스타일 분석
    // 가장 유사한 스피치 스타일
    @Column(name = "most_similar_style")
    private String mostSimilarStyle;
    // 스타일 일치율
    @Column(name = "matching_rate")
    private Integer matchingRate;
    // 스타일 분석 상세 설명
    @Column(name = "style_description", columnDefinition = "TEXT")
    private String styleDescription;

    // 3. AI 상세 진단 (AI Report)
    // 긍정적 피드백 제목
    @Column(name = "positive_title")
    private String positiveTitle;
    // 긍정적 피드백 상세 설명
    @Column(name = "positive_description", columnDefinition = "TEXT")
    private String positiveDescription;
    // 개선할 점 제목
    @Column(name = "improvement_title")
    private String improvementTitle;
    // 개선할 점 상세 설명
    @Column(name = "improvement_description", columnDefinition = "TEXT")
    private String improvementDescription;

    // 4. 연습 가이드 요약
    @Column(name = "guide_summary", columnDefinition = "TEXT")
    private String guideSummary;
    // 연습 가이드 다음 단계
    @Column(name = "guide_next_step", columnDefinition = "TEXT")
    private String guideNextStep;

    // AI 분석 완료 시 데이터를 업데이트하는 비즈니스 메소드입니다.
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
        this.status = FeedbackStatus.COMPLETED; // 분석 완료 상태로 변경
    }

    // AI 분석 실패 시 상태를 FAILED로 변경하는 비즈니스 메소드입니다.
    public void failAnalysis() {
        this.status = FeedbackStatus.FAILED;
    }
}
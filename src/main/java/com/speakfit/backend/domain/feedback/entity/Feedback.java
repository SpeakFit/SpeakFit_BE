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

    @Column(name = "ai_feedback", columnDefinition = "TEXT")
    private String aiFeedback;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private FeedbackStatus status;

    // 피드백 상태 업데이트
    public void updateFeedback(String aiFeedback, FeedbackStatus status) {
        this.aiFeedback = aiFeedback;
        this.status = status;
    }
}
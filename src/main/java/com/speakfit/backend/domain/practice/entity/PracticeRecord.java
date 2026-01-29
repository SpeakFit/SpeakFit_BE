package com.speakfit.backend.domain.practice.entity;

import com.speakfit.backend.domain.practice.enums.PracticeStatus;
import com.speakfit.backend.domain.script.entity.Script;
import com.speakfit.backend.domain.user.entity.User;
import com.speakfit.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "practice_record")
public class PracticeRecord extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "script_id", nullable = false)
    private Script script;

    @Column(name = "audio_url", columnDefinition = "TEXT")
    private String audioUrl; // 초기 생성 시에는 null 일 수 있음

    @Column(name = "time")
    private Double time; // 연습 시간 (초 단위 예상)

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PracticeStatus status;

    // 연습 종료 시 업데이트를 위한 편의 메서드
    public void stopPractice(String audioUrl, Double time) {
        this.audioUrl = audioUrl;
        this.time = time;
        this.status = PracticeStatus.COMPLETED;
    }
}
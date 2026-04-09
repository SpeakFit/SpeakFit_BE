package com.speakfit.backend.domain.practice.entity;

import com.speakfit.backend.domain.practice.enums.PracticeStatus;
import com.speakfit.backend.domain.script.entity.Script;
import com.speakfit.backend.domain.style.entity.SpeechStyle;
import com.speakfit.backend.domain.user.entity.User;
import com.speakfit.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "style_id", nullable = false)
    private SpeechStyle speechStyle;

    @Column(name = "audio_url", columnDefinition = "TEXT")
    private String audioUrl;

    @Column(name = "time")
    private Double time;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PracticeStatus status;

    @Column(name = "audience_type")
    private String audienceType; // String (Varchar) - Enum 필요시 추후 변경

    @Column(name = "audience_understanding")
    private String audienceUnderstanding; // String (Varchar)

    @Column(name = "speech_information")
    private String speechInformation; // String (Varchar)

    // 연관관계 매핑 (1:1 및 1:N)
    @OneToOne(mappedBy = "practiceRecord", cascade = CascadeType.ALL, orphanRemoval = true)
    private AnalysisResult analysisResult;

    @OneToOne(mappedBy = "practiceRecord", cascade = CascadeType.ALL, orphanRemoval = true)
    private AiAnalysisResult aiAnalysisResult;

    @Builder.Default
    @OneToMany(mappedBy = "practiceRecord", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PracticeIssue> practiceIssues = new ArrayList<>();

    // 상태 변경 편의 메서드
    public void stopRecording(String audioUrl, Double time) {
        this.audioUrl = audioUrl;
        this.time = time;
        this.status = PracticeStatus.COMPLETED;
    }

    public void updateStatus(PracticeStatus status) {
        this.status = status;
    }
}

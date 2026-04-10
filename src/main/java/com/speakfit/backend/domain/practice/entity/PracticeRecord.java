package com.speakfit.backend.domain.practice.entity;

import com.speakfit.backend.domain.practice.enums.AudienceType;
import com.speakfit.backend.domain.practice.enums.AudienceUnderstanding;
import com.speakfit.backend.domain.practice.enums.SpeechInformation;
import com.speakfit.backend.domain.practice.enums.Status;
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
    @JoinColumn(name = "style_id", nullable = true)
    private SpeechStyle speechStyle;

    @Column(name = "audio_url", columnDefinition = "TEXT")
    private String audioUrl;

    @Column(name = "time")
    private Double time;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @Enumerated(EnumType.STRING)
    @Column(name = "audience_type")
    private AudienceType audienceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "audience_understanding")
    private AudienceUnderstanding audienceUnderstanding;

    @Enumerated(EnumType.STRING)
    @Column(name = "speech_information")
    private SpeechInformation speechInformation;

    @OneToOne(mappedBy = "practiceRecord", cascade = CascadeType.ALL, orphanRemoval = true)
    private AnalysisResult analysisResult;

    @OneToOne(mappedBy = "practiceRecord", cascade = CascadeType.ALL, orphanRemoval = true)
    private AiAnalysisResult aiAnalysisResult;

    @Builder.Default
    @OneToMany(mappedBy = "practiceRecord", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PracticeIssue> practiceIssues = new ArrayList<>();

    public void stopRecording(String audioUrl, Double time) {
        this.audioUrl = audioUrl;
        this.time = time;
        this.status = Status.COMPLETED;
    }

    public void updateStatus(Status status) {
        this.status = status;
    }

    public void selectStyle(SpeechStyle speechStyle) {
        this.speechStyle = speechStyle;
    }
}

package com.speakfit.backend.domain.practice.entity;

import com.speakfit.backend.domain.practice.enums.DetailStatus;
import com.speakfit.backend.domain.script.entity.ScriptSentence;
import com.speakfit.backend.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "practice_sentence_result", indexes = {
        @Index(name = "idx_practice_sentence_result_record_id", columnList = "record_id"),
        @Index(name = "idx_practice_sentence_result_record_sentence", columnList = "record_id, sentence_index")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class PracticeSentenceResult extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "record_id", nullable = false)
    private PracticeRecord practiceRecord;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "script_sentence_id")
    private ScriptSentence scriptSentence;

    @Column(name = "sentence_index", nullable = false)
    private Integer sentenceIndex;

    @Column(name = "start_ms")
    private Long startMs;

    @Column(name = "end_ms")
    private Long endMs;

    @Column(name = "word_count")
    private Integer wordCount;

    @Column(name = "skipped_word_count")
    private Integer skippedWordCount;

    @Column(name = "wpm")
    private Double wpm;

    @Column(name = "pause_duration_ms")
    private Long pauseDurationMs;

    @Column(name = "avg_pitch")
    private Double avgPitch;

    @Column(name = "avg_intensity")
    private Double avgIntensity;

    @Column(name = "score")
    private Double score;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private DetailStatus status;
}

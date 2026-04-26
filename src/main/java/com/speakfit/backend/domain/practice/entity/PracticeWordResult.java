package com.speakfit.backend.domain.practice.entity;

import com.speakfit.backend.domain.practice.enums.DetailStatus;
import com.speakfit.backend.domain.script.entity.ScriptWord;
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
@Table(name = "practice_word_result", indexes = {
        @Index(name = "idx_practice_word_result_record_id", columnList = "record_id"),
        @Index(name = "idx_practice_word_result_record_global", columnList = "record_id, global_word_index")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class PracticeWordResult extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "record_id", nullable = false)
    private PracticeRecord practiceRecord;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "script_word_id")
    private ScriptWord scriptWord;

    @Column(name = "global_word_index", nullable = false)
    private Integer globalWordIndex;

    @Column(name = "sentence_word_index")
    private Integer sentenceWordIndex;

    @Column(name = "start_ms")
    private Long startMs;

    @Column(name = "end_ms")
    private Long endMs;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "skipped", nullable = false)
    private Boolean skipped;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private DetailStatus status;
}

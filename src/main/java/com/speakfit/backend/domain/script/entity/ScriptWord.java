package com.speakfit.backend.domain.script.entity;

import com.speakfit.backend.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "script_word", indexes = {
        @Index(name = "idx_script_word_sentence_id", columnList = "script_sentence_id"),
        @Index(name = "idx_script_word_global_index", columnList = "global_word_index")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ScriptWord extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "script_sentence_id", nullable = false)
    private ScriptSentence scriptSentence;

    @Column(name = "global_word_index", nullable = false)
    private Integer globalWordIndex;

    @Column(name = "sentence_word_index", nullable = false)
    private Integer sentenceWordIndex;

    @Column(name = "text", columnDefinition = "TEXT", nullable = false)
    private String text;

    @Column(name = "normalized_text", columnDefinition = "TEXT")
    private String normalizedText;

    @Column(name = "start_char_index", nullable = false)
    private Integer startCharIndex;

    @Column(name = "end_char_index", nullable = false)
    private Integer endCharIndex;

    // 대본 문장 연관관계 설정 구현
    protected void setScriptSentence(ScriptSentence scriptSentence) {
        this.scriptSentence = scriptSentence;
    }
}

package com.speakfit.backend.domain.script.entity;

import com.speakfit.backend.global.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "script_sentence", indexes = {
        @Index(name = "idx_script_sentence_script_id", columnList = "script_id"),
        @Index(name = "idx_script_sentence_script_sentence_index", columnList = "script_id, sentence_index")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ScriptSentence extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "script_id", nullable = false)
    private Script script;

    @Column(name = "sentence_index", nullable = false)
    private Integer sentenceIndex;

    @Column(name = "original_text", columnDefinition = "TEXT", nullable = false)
    private String originalText;

    @Column(name = "normalized_text", columnDefinition = "TEXT")
    private String normalizedText;

    @Column(name = "start_char_index", nullable = false)
    private Integer startCharIndex;

    @Column(name = "end_char_index", nullable = false)
    private Integer endCharIndex;

    @Builder.Default
    @OneToMany(mappedBy = "scriptSentence", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ScriptWord> scriptWords = new ArrayList<>();

    // 대본 연관관계 설정 구현
    protected void setScript(Script script) {
        this.script = script;
    }

    // 대본 단어 연관관계 추가 구현
    public void addScriptWord(ScriptWord scriptWord) {
        this.scriptWords.add(scriptWord);
        if (scriptWord.getScriptSentence() != this) {
            scriptWord.setScriptSentence(this);
        }
    }
}

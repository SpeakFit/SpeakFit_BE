package com.speakfit.backend.domain.script.service;

import com.speakfit.backend.domain.script.entity.ScriptSentence;
import com.speakfit.backend.domain.script.entity.ScriptWord;
import org.springframework.stereotype.Component;

import java.text.BreakIterator;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ScriptContentParser {

    private static final Pattern WORD_PATTERN = Pattern.compile("\\S+");
    private static final Pattern NORMALIZE_SYMBOL_PATTERN = Pattern.compile("[^\\p{IsAlphabetic}\\p{IsDigit}\\p{IsHangul}]+");

    // 대본 원문을 문장과 단어 단위로 파싱 구현
    public List<ScriptSentence> parse(String content) {
        List<ScriptSentence> sentences = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return sentences;
        }

        BreakIterator sentenceIterator = BreakIterator.getSentenceInstance(Locale.KOREAN);
        sentenceIterator.setText(content);

        int globalWordIndex = 0;
        int sentenceIndex = 0;
        int start = sentenceIterator.first();
        for (int end = sentenceIterator.next(); end != BreakIterator.DONE; start = end, end = sentenceIterator.next()) {
            String sentenceText = content.substring(start, end);
            if (sentenceText.isBlank()) {
                continue;
            }

            ScriptSentence sentence = ScriptSentence.builder()
                    .sentenceIndex(sentenceIndex++)
                    .originalText(sentenceText)
                    .normalizedText(normalizeText(sentenceText))
                    .startCharIndex(start)
                    .endCharIndex(end)
                    .build();

            Matcher wordMatcher = WORD_PATTERN.matcher(sentenceText);
            int sentenceWordIndex = 0;
            while (wordMatcher.find()) {
                String wordText = wordMatcher.group();
                ScriptWord word = ScriptWord.builder()
                        .globalWordIndex(globalWordIndex++)
                        .sentenceWordIndex(sentenceWordIndex++)
                        .text(wordText)
                        .normalizedText(normalizeText(wordText))
                        .startCharIndex(start + wordMatcher.start())
                        .endCharIndex(start + wordMatcher.end())
                        .build();
                sentence.addScriptWord(word);
            }

            sentences.add(sentence);
        }

        return sentences;
    }

    // 비교용 정규화 텍스트 생성 구현
    private String normalizeText(String text) {
        if (text == null) {
            return "";
        }

        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        return NORMALIZE_SYMBOL_PATTERN.matcher(normalized).replaceAll("");
    }
}

package com.speakfit.backend.domain.practice.service;

import com.speakfit.backend.domain.practice.dto.res.PythonAnalysisRes;
import com.speakfit.backend.domain.practice.entity.AiAnalysisResult;
import com.speakfit.backend.domain.practice.entity.AnalysisResult;
import com.speakfit.backend.domain.practice.entity.PracticeIssue;
import com.speakfit.backend.domain.practice.entity.PracticeRecord;
import com.speakfit.backend.domain.practice.entity.PracticeSentenceResult;
import com.speakfit.backend.domain.practice.entity.PracticeWordResult;
import com.speakfit.backend.domain.practice.enums.DetailStatus;
import com.speakfit.backend.domain.practice.enums.Status;
import com.speakfit.backend.domain.practice.exception.PracticeErrorCode;
import com.speakfit.backend.domain.practice.repository.AiAnalysisResultRepository;
import com.speakfit.backend.domain.practice.repository.AnalysisResultRepository;
import com.speakfit.backend.domain.practice.repository.PracticeIssueRepository;
import com.speakfit.backend.domain.practice.repository.PracticeRepository;
import com.speakfit.backend.domain.practice.repository.PracticeSentenceResultRepository;
import com.speakfit.backend.domain.practice.repository.PracticeWordResultRepository;
import com.speakfit.backend.domain.script.entity.ScriptSentence;
import com.speakfit.backend.domain.script.entity.ScriptWord;
import com.speakfit.backend.domain.script.repository.ScriptSentenceRepository;
import com.speakfit.backend.domain.script.repository.ScriptWordRepository;
import com.speakfit.backend.global.apiPayload.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiAnalysisTxServiceImpl implements AiAnalysisTxService {

    private final PracticeRepository practiceRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final AiAnalysisResultRepository aiAnalysisResultRepository;
    private final PracticeIssueRepository practiceIssueRepository;
    private final PracticeWordResultRepository practiceWordResultRepository;
    private final PracticeSentenceResultRepository practiceSentenceResultRepository;
    private final ScriptWordRepository scriptWordRepository;
    private final ScriptSentenceRepository scriptSentenceRepository;

    // 분석 결과 데이터 저장 로직 구현
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveResults(Long practiceId, PythonAnalysisRes data) {
        // 기존에 만든 findByIdWithDetails가 있다면 그것을 사용하세요.
        PracticeRecord record = practiceRepository.findById(practiceId)
                .orElseThrow(() -> new CustomException(PracticeErrorCode.PRACTICE_NOT_FOUND));

        // 1. 정량 지표 데이터 저장
        AnalysisResult analysisResult = analysisResultRepository.findByPracticeRecord(record)
                .orElseGet(() -> AnalysisResult.builder().practiceRecord(record).build());

        analysisResult.updateData(
                data.getAvgWpm(), data.getAvgPitch(), data.getAvgIntensity(), data.getAvgZcr(),
                data.getPauseRatio(), data.getWpmDiff(), data.getPitchDiff(),
                data.getIntensityDiff(), data.getZcrDiff(), data.getPauseCount()
        );
        analysisResultRepository.save(analysisResult);

        // 2. AI 상세 피드백 저장
        AiAnalysisResult aiResult = aiAnalysisResultRepository.findByPracticeRecord(record)
                .orElseGet(() -> AiAnalysisResult.builder().practiceRecord(record).build());

        // 엔티티에 추가한 updateAiData 메서드 호출
        aiResult.updateAiData(data);
        aiAnalysisResultRepository.save(aiResult);

        // 3. 단어 단위 정렬 결과 저장
        saveWordResults(record, data.getWordResults());

        // 4. 문장 단위 분석 결과 저장
        saveSentenceResults(record, data.getSentenceResults());

        // 5. 문제 구간 결과 저장
        saveIssues(record, data.getIssues());

        // 6. 상태 변경
        record.updateStatus(Status.ANALYZED);
        practiceRepository.saveAndFlush(record);
    }

    // 단어 단위 분석 결과 저장 구현
    private void saveWordResults(PracticeRecord record, List<PythonAnalysisRes.WordResult> wordResults) {
        if (wordResults == null || wordResults.isEmpty()) {
            return;
        }

        practiceWordResultRepository.deleteAllByPracticeRecordId(record.getId());

        Map<Integer, ScriptWord> scriptWordMap = scriptWordRepository
                .findAllByScriptSentenceScriptIdOrderByGlobalWordIndexAsc(record.getScript().getId())
                .stream()
                .collect(Collectors.toMap(ScriptWord::getGlobalWordIndex, Function.identity()));

        List<PracticeWordResult> results = wordResults.stream()
                .filter(wordResult -> wordResult.getResolvedGlobalWordIndex() != null)
                .map(wordResult -> toPracticeWordResult(record, wordResult, scriptWordMap.get(wordResult.getResolvedGlobalWordIndex())))
                .toList();

        practiceWordResultRepository.saveAll(results);
    }

    // 단어 단위 분석 결과 엔티티 변환 구현
    private PracticeWordResult toPracticeWordResult(PracticeRecord record, PythonAnalysisRes.WordResult wordResult, ScriptWord scriptWord) {
        Integer globalWordIndex = wordResult.getResolvedGlobalWordIndex();
        Integer sentenceWordIndex = wordResult.getSentenceWordIndex();
        if (sentenceWordIndex == null && scriptWord != null) {
            sentenceWordIndex = scriptWord.getSentenceWordIndex();
        }

        return PracticeWordResult.builder()
                .practiceRecord(record)
                .scriptWord(scriptWord)
                .globalWordIndex(globalWordIndex)
                .sentenceWordIndex(sentenceWordIndex)
                .startMs(wordResult.getStartMs())
                .endMs(wordResult.getEndMs())
                .confidence(wordResult.getConfidence())
                .skipped(Boolean.TRUE.equals(wordResult.getSkipped()))
                .status(resolveWordStatus(wordResult))
                .build();
    }

    // 단어 결과 상태 결정 구현
    private DetailStatus resolveWordStatus(PythonAnalysisRes.WordResult wordResult) {
        if (Boolean.TRUE.equals(wordResult.getSkipped())) {
            return DetailStatus.MISMATCH;
        }

        if (wordResult.getStatus() != null) {
            return wordResult.getStatus();
        }

        return DetailStatus.NORMAL;
    }

    // 문장 단위 분석 결과 저장 구현
    private void saveSentenceResults(PracticeRecord record, List<PythonAnalysisRes.SentenceResult> sentenceResults) {
        if (sentenceResults == null || sentenceResults.isEmpty()) {
            return;
        }

        practiceSentenceResultRepository.deleteAllByPracticeRecordId(record.getId());

        Map<Integer, ScriptSentence> sentenceIndexMap = scriptSentenceRepository
                .findAllByScriptIdOrderBySentenceIndexAsc(record.getScript().getId())
                .stream()
                .collect(Collectors.toMap(ScriptSentence::getSentenceIndex, Function.identity()));
        Map<Long, ScriptSentence> sentenceIdMap = sentenceIndexMap.values()
                .stream()
                .filter(sentence -> sentence.getId() != null)
                .collect(Collectors.toMap(ScriptSentence::getId, Function.identity()));

        List<PracticeSentenceResult> results = sentenceResults.stream()
                .filter(sentenceResult -> sentenceResult.getSentenceIndex() != null || sentenceResult.getScriptSentenceId() != null)
                .map(sentenceResult -> toPracticeSentenceResult(record, sentenceResult, resolveScriptSentence(sentenceResult, sentenceIndexMap, sentenceIdMap)))
                .filter(Objects::nonNull)
                .toList();

        practiceSentenceResultRepository.saveAll(results);
    }

    // 문장 단위 분석 결과 엔티티 변환 구현
    private PracticeSentenceResult toPracticeSentenceResult(PracticeRecord record, PythonAnalysisRes.SentenceResult sentenceResult, ScriptSentence scriptSentence) {
        Integer sentenceIndex = sentenceResult.getSentenceIndex();
        if (sentenceIndex == null && scriptSentence != null) {
            sentenceIndex = scriptSentence.getSentenceIndex();
        }
        if (sentenceIndex == null) {
            return null;
        }

        return PracticeSentenceResult.builder()
                .practiceRecord(record)
                .scriptSentence(scriptSentence)
                .sentenceIndex(sentenceIndex)
                .startMs(sentenceResult.getStartMs())
                .endMs(sentenceResult.getEndMs())
                .wordCount(resolveWordCount(sentenceResult, scriptSentence))
                .skippedWordCount(resolveSkippedWordCount(sentenceResult))
                .wpm(sentenceResult.getWpm())
                .pauseDurationMs(sentenceResult.getPauseDurationMs())
                .avgPitch(sentenceResult.getAvgPitch())
                .avgIntensity(sentenceResult.getAvgIntensity())
                .score(sentenceResult.getScore())
                .status(resolveSentenceStatus(sentenceResult))
                .build();
    }

    // 분석 결과 문장 매칭 구현
    private ScriptSentence resolveScriptSentence(PythonAnalysisRes.SentenceResult sentenceResult,
                                                 Map<Integer, ScriptSentence> sentenceIndexMap,
                                                 Map<Long, ScriptSentence> sentenceIdMap) {
        if (sentenceResult.getScriptSentenceId() != null) {
            ScriptSentence sentence = sentenceIdMap.get(sentenceResult.getScriptSentenceId());
            if (sentence != null) {
                return sentence;
            }
        }

        return sentenceIndexMap.get(sentenceResult.getSentenceIndex());
    }

    // 문장 단어 수 결정 구현
    private Integer resolveWordCount(PythonAnalysisRes.SentenceResult sentenceResult, ScriptSentence scriptSentence) {
        if (sentenceResult.getWordCount() != null) {
            return sentenceResult.getWordCount();
        }

        if (scriptSentence == null || scriptSentence.getScriptWords() == null) {
            return null;
        }

        return scriptSentence.getScriptWords().size();
    }

    // 문장 누락 단어 수 결정 구현
    private Integer resolveSkippedWordCount(PythonAnalysisRes.SentenceResult sentenceResult) {
        if (sentenceResult.getSkippedWordCount() != null) {
            return sentenceResult.getSkippedWordCount();
        }

        return 0;
    }

    // 문장 결과 상태 결정 구현
    private DetailStatus resolveSentenceStatus(PythonAnalysisRes.SentenceResult sentenceResult) {
        if (sentenceResult.getStatus() != null) {
            return sentenceResult.getStatus();
        }

        return DetailStatus.NORMAL;
    }

    // 문제 구간 분석 결과 저장 구현
    private void saveIssues(PracticeRecord record, List<PythonAnalysisRes.IssueResult> issues) {
        if (issues == null || issues.isEmpty()) {
            return;
        }

        practiceIssueRepository.deleteAllByPracticeRecordId(record.getId());

        Map<Integer, ScriptSentence> sentenceIndexMap = scriptSentenceRepository
                .findAllByScriptIdOrderBySentenceIndexAsc(record.getScript().getId())
                .stream()
                .collect(Collectors.toMap(ScriptSentence::getSentenceIndex, Function.identity()));
        Map<Long, ScriptSentence> sentenceIdMap = sentenceIndexMap.values()
                .stream()
                .filter(sentence -> sentence.getId() != null)
                .collect(Collectors.toMap(ScriptSentence::getId, Function.identity()));

        AtomicInteger defaultDisplayOrder = new AtomicInteger(0);
        List<PracticeIssue> results = issues.stream()
                .limit(5)
                .map(issue -> toPracticeIssue(record, issue, resolveScriptSentence(issue, sentenceIndexMap, sentenceIdMap), defaultDisplayOrder.getAndIncrement()))
                .filter(Objects::nonNull)
                .toList();

        practiceIssueRepository.saveAll(results);
    }

    // 문제 구간 결과 엔티티 변환 구현
    private PracticeIssue toPracticeIssue(PracticeRecord record,
                                          PythonAnalysisRes.IssueResult issue,
                                          ScriptSentence scriptSentence,
                                          Integer defaultDisplayOrder) {
        Integer sentenceIndex = issue.getSentenceIndex();
        if (sentenceIndex == null && scriptSentence != null) {
            sentenceIndex = scriptSentence.getSentenceIndex();
        }

        return PracticeIssue.builder()
                .practiceRecord(record)
                .scriptSentence(scriptSentence)
                .issueType(issue.getIssueType())
                .sentenceIndex(sentenceIndex)
                .startIndex(issue.getStartIndex())
                .endIndex(issue.getEndIndex())
                .issueSummary(issue.getIssueSummary())
                .feedbackContent(issue.getFeedbackContent())
                .reason(issue.getReason())
                .score(issue.getScore())
                .displayOrder(resolveIssueDisplayOrder(issue, defaultDisplayOrder))
                .wpm(issue.getWpm())
                .intensity(issue.getIntensity())
                .build();
    }

    // 문제 구간 문장 매칭 구현
    private ScriptSentence resolveScriptSentence(PythonAnalysisRes.IssueResult issue,
                                                 Map<Integer, ScriptSentence> sentenceIndexMap,
                                                 Map<Long, ScriptSentence> sentenceIdMap) {
        if (issue.getScriptSentenceId() != null) {
            ScriptSentence sentence = sentenceIdMap.get(issue.getScriptSentenceId());
            if (sentence != null) {
                return sentence;
            }
        }

        return sentenceIndexMap.get(issue.getSentenceIndex());
    }

    // 문제 구간 정렬 순서 결정 구현
    private Integer resolveIssueDisplayOrder(PythonAnalysisRes.IssueResult issue, Integer defaultDisplayOrder) {
        if (issue.getDisplayOrder() != null) {
            return issue.getDisplayOrder();
        }

        return defaultDisplayOrder;
    }

    // 분석 실패 시 상태 처리 로직 구현
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleAnalysisFailure(Long practiceId) {
        practiceRepository.findById(practiceId).ifPresent(record -> {
            record.updateStatus(Status.FAILED);
            practiceRepository.save(record);
        });
    }
}

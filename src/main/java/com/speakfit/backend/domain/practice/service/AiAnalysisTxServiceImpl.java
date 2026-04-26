package com.speakfit.backend.domain.practice.service;

import com.speakfit.backend.domain.practice.dto.res.PythonAnalysisRes;
import com.speakfit.backend.domain.practice.entity.AiAnalysisResult;
import com.speakfit.backend.domain.practice.entity.AnalysisResult;
import com.speakfit.backend.domain.practice.entity.PracticeRecord;
import com.speakfit.backend.domain.practice.entity.PracticeWordResult;
import com.speakfit.backend.domain.practice.enums.DetailStatus;
import com.speakfit.backend.domain.practice.enums.Status;
import com.speakfit.backend.domain.practice.exception.PracticeErrorCode;
import com.speakfit.backend.domain.practice.repository.AiAnalysisResultRepository;
import com.speakfit.backend.domain.practice.repository.AnalysisResultRepository;
import com.speakfit.backend.domain.practice.repository.PracticeRepository;
import com.speakfit.backend.domain.practice.repository.PracticeWordResultRepository;
import com.speakfit.backend.domain.script.entity.ScriptWord;
import com.speakfit.backend.domain.script.repository.ScriptWordRepository;
import com.speakfit.backend.global.apiPayload.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiAnalysisTxServiceImpl implements AiAnalysisTxService {

    private final PracticeRepository practiceRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final AiAnalysisResultRepository aiAnalysisResultRepository;
    private final PracticeWordResultRepository practiceWordResultRepository;
    private final ScriptWordRepository scriptWordRepository;

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

        // 4. 상태 변경
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

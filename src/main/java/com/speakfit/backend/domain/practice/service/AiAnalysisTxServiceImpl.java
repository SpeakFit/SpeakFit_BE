package com.speakfit.backend.domain.practice.service;

import com.speakfit.backend.domain.practice.dto.res.PythonAnalysisRes;
import com.speakfit.backend.domain.practice.entity.AiAnalysisResult;
import com.speakfit.backend.domain.practice.entity.AnalysisResult;
import com.speakfit.backend.domain.practice.entity.PracticeRecord;
import com.speakfit.backend.domain.practice.enums.Status;
import com.speakfit.backend.domain.practice.exception.PracticeErrorCode;
import com.speakfit.backend.domain.practice.repository.AiAnalysisResultRepository;
import com.speakfit.backend.domain.practice.repository.AnalysisResultRepository;
import com.speakfit.backend.domain.practice.repository.PracticeRepository;
import com.speakfit.backend.global.apiPayload.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiAnalysisTxServiceImpl implements AiAnalysisTxService {

    private final PracticeRepository practiceRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final AiAnalysisResultRepository aiAnalysisResultRepository;

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

        // 3. 상태 변경
        record.updateStatus(Status.ANALYZED);
        practiceRepository.saveAndFlush(record);
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

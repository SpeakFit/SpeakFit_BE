package com.speakfit.backend.domain.practice.service;

import com.speakfit.backend.domain.practice.dto.res.PythonAnalysisRes;
import com.speakfit.backend.domain.practice.entity.AiAnalysisResult;
import com.speakfit.backend.domain.practice.entity.AnalysisResult;
import com.speakfit.backend.domain.practice.entity.PracticeRecord;
import com.speakfit.backend.domain.practice.enums.PracticeStatus;
import com.speakfit.backend.domain.practice.repository.AiAnalysisResultRepository;
import com.speakfit.backend.domain.practice.repository.AnalysisResultRepository;
import com.speakfit.backend.domain.practice.repository.PracticeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AiAnalysisService {

    private final PracticeRepository practiceRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final AiAnalysisResultRepository aiAnalysisResultRepository;
    private final WebClient webClient;

    //비동기 분석 및 결과 저장 로직
    @Async
    @Transactional
    public void processAnalysisAsync(Long practiceId, String audioUrl) {
        try {
            // 1. 파이썬 서버에 분석 요청
            PythonAnalysisRes pythonData = requestPythonAnalysis(practiceId, audioUrl);

            // 2. 응답 데이터가 있으면 결과 저장
            if (pythonData != null) {
                saveResults(practiceId, pythonData);
            }
        } catch (Exception e) {
            System.err.println("비동기 분석 실패: " + e.getMessage());
            // 3. 에러 발생 시 실패 상태로 변경
            handleAnalysisFailure(practiceId);
        }
    }

    @Transactional
    public void saveResults(Long practiceId, PythonAnalysisRes data) {
        PracticeRecord record = practiceRepository.findById(practiceId).orElseThrow();

        // 1. 정량 결과 저장 (Update or Insert)
        AnalysisResult analysisResult = analysisResultRepository.findByPracticeRecord(record)
                .orElse(AnalysisResult.builder().practiceRecord(record).build());

        // 데이터 업데이트 (새로 만든 객체든 기존 객체든 값을 채워넣음)
        analysisResult.updateData(
                data.getAvgWpm(), data.getAvgPitch(), data.getAvgIntensity(), data.getAvgZcr(),
                data.getPauseRatio(), data.getWpmDiff(), data.getPitchDiff(),
                data.getIntensityDiff(), data.getZcrDiff(), data.getPauseCount()
        );
        analysisResultRepository.save(analysisResult); // 있으면 update 쿼리, 없으면 insert 쿼리 나감

        // 2. AI 총평 저장 (Update or Insert)
        AiAnalysisResult aiAnalysisResult = aiAnalysisResultRepository.findByPracticeRecord(record)
                .orElse(AiAnalysisResult.builder().practiceRecord(record).build());

        aiAnalysisResult.updateSummary(data.getAiSummary());
        aiAnalysisResultRepository.save(aiAnalysisResult);

        // 3. 상태 변경
        record.updateStatus(PracticeStatus.ANALYZED);
        practiceRepository.save(record);

        System.out.println("분석 성공 & 데이터 업데이트 완료 (ID: " + practiceId + ")");
    }


    @Transactional
    public void handleAnalysisFailure(Long practiceId) {
        practiceRepository.findById(practiceId).ifPresent(record -> {
            // 분석 실패 시 상태를 FAILED로 변경하여 사용자에게 알림
            record.updateStatus(PracticeStatus.FAILED);
            practiceRepository.save(record);
        });
    }

    private PythonAnalysisRes requestPythonAnalysis(Long practiceId, String audioUrl) {
        Map<String, Object> body = new HashMap<>();
        body.put("practiceId", practiceId);
        body.put("audioUrl", audioUrl);

        // WebClientConfig에 설정된 Base URL을 사용합니다.
        return webClient.post()
                .uri("/analyze")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(PythonAnalysisRes.class)
                .block(); // 비동기 스레드 내부이므로 block()을 통한 동기 대기가 가능합니다.
    }
}
package com.speakfit.backend.domain.practice.service;

import com.speakfit.backend.domain.practice.dto.res.PythonAnalysisRes;
import com.speakfit.backend.domain.practice.entity.AiAnalysisResult;
import com.speakfit.backend.domain.practice.entity.AnalysisResult;
import com.speakfit.backend.domain.practice.entity.PracticeRecord;
import com.speakfit.backend.domain.practice.enums.Status;
import com.speakfit.backend.domain.practice.repository.AiAnalysisResultRepository;
import com.speakfit.backend.domain.practice.repository.AnalysisResultRepository;
import com.speakfit.backend.domain.practice.repository.PracticeRepository;
import com.speakfit.backend.domain.script.entity.Script;
import com.speakfit.backend.domain.style.entity.SpeechStyle;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiAnalysisService {

    private final PracticeRepository practiceRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final AiAnalysisResultRepository aiAnalysisResultRepository;
    private final WebClient webClient;

    // 비동기 분석 및 결과 저장 로직 구현
    @Async
    @Transactional
    public void processAnalysisAsync(Long practiceId, String audioUrl) {
        try {
            PracticeRecord record = practiceRepository.findByIdWithDetails(practiceId)
                    .orElseThrow(() -> new RuntimeException("연습 기록을 찾을 수 없습니다."));
            
            // 1. 파이썬 분석 서버에 데이터 요청 (모든 컨텍스트 포함)
            PythonAnalysisRes pythonData = requestPythonAnalysis(record, audioUrl);

            // 2. 응답이 성공적이면 결과 저장
            if (pythonData != null) {
                saveResults(practiceId, pythonData);
            }
        } catch (Exception e) {
            System.err.println("비동기 분석 실패: " + e.getMessage());
            handleAnalysisFailure(practiceId);
        }
    }

    // 파이썬 서버로부터 받은 분석 결과를 DB에 저장
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveResults(Long practiceId, PythonAnalysisRes data) {
        PracticeRecord record = practiceRepository.findByIdWithDetails(practiceId).orElseThrow();

        // 1. 정량 지표 데이터 저장/업데이트 (기존과 동일)
        AnalysisResult analysisResult = analysisResultRepository.findByPracticeRecord(record)
                .orElse(AnalysisResult.builder().practiceRecord(record).build());

        analysisResult.updateData(
                data.getAvgWpm(), data.getAvgPitch(), data.getAvgIntensity(), data.getAvgZcr(),
                data.getPauseRatio(), data.getWpmDiff(), data.getPitchDiff(),
                data.getIntensityDiff(), data.getZcrDiff(), data.getPauseCount()
        );
        analysisResultRepository.save(analysisResult);

        // 2. AI 상세 피드백(AiAnalysisResult) 저장/업데이트
        // findByPracticeRecord로 조회 시도
        AiAnalysisResult aiResult = aiAnalysisResultRepository.findByPracticeRecord(record)
                .orElse(AiAnalysisResult.builder().practiceRecord(record).build()); // recordId() 설정하지 않음!

        aiResult.updateAiData(data);
        aiAnalysisResultRepository.save(aiResult);

        // 3. 상태 변경
        record.updateStatus(Status.ANALYZED);
        practiceRepository.save(record);
    }

    @Transactional
    public void handleAnalysisFailure(Long practiceId) {
        practiceRepository.findById(practiceId).ifPresent(record -> {
            record.updateStatus(Status.FAILED);
            practiceRepository.save(record);
        });
    }

    // 파이썬 분석 서버와의 실제 통신 로직 완성
    private PythonAnalysisRes requestPythonAnalysis(PracticeRecord record, String audioUrl) {
        // 파이썬 서버가 분석에 필요한 모든 데이터를 Map에 담음
        Map<String, Object> body = new HashMap<>();
        body.put("practiceId", record.getId());
        body.put("audioUrl", audioUrl);
        body.put("markedContent", record.getScript().getMarkedContent()); // 낭독 기호 대본
        body.put("audienceType", record.getAudienceType().toString()); // Enum -> String
        body.put("audienceUnderstanding", record.getAudienceUnderstanding().toString());
        body.put("speechInformation", record.getSpeechInformation().toString());
        body.put("styleType", record.getSpeechStyle().getStyleType()); // 스타일 정보

        // 파이썬 서버의 /analyze 엔드포인트 호출
        return webClient.post()
                .uri("/analyze")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(PythonAnalysisRes.class)
                .block(); // 비동기 스레드 내에서 호출되므로 block() 사용 가능
    }

    // 파이썬 서버에 대본 기호 생성(Marking) 요청
    public String generateMarkedContent(Script script, SpeechStyle style, PracticeRecord record) {
        Map<String, Object> body = new HashMap<>();
        body.put("content", script.getContent());
        body.put("style", style.getStyleType());
        body.put("audienceType", record.getAudienceType().toString());
        body.put("audienceUnderstanding", record.getAudienceUnderstanding().toString());
        body.put("speechInformation", record.getSpeechInformation().toString());

        try {
            // /scripts/mark 엔드포인트 호출
            Map<String, Object> response = webClient.post()
                    .uri("/scripts/mark")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            return response != null ? (String) response.get("markedContent") : null;
        } catch (Exception e) {
            System.err.println("AI 기호 대본 생성 실패: " + e.getMessage());
            return null;
        }
    }
}

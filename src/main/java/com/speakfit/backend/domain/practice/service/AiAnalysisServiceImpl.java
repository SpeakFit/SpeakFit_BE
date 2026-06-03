package com.speakfit.backend.domain.practice.service;

import com.speakfit.backend.domain.metric.dto.TargetMetricResult;
import com.speakfit.backend.domain.metric.service.TargetMetricCalculator;
import com.speakfit.backend.domain.practice.dto.res.PythonAnalysisRes;
import com.speakfit.backend.domain.practice.entity.PracticeRecord;
import com.speakfit.backend.domain.practice.exception.PracticeErrorCode;
import com.speakfit.backend.domain.practice.repository.AiAnalysisResultRepository;
import com.speakfit.backend.domain.practice.repository.AnalysisResultRepository;
import com.speakfit.backend.domain.practice.repository.PracticeRepository;
import com.speakfit.backend.domain.script.entity.Script;
import com.speakfit.backend.domain.script.entity.ScriptWord;
import com.speakfit.backend.domain.script.repository.ScriptWordRepository;
import com.speakfit.backend.domain.script.service.ScriptTxService;
import com.speakfit.backend.domain.style.entity.SpeechStyle;
import com.speakfit.backend.global.apiPayload.exception.CustomException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AiAnalysisServiceImpl implements AiAnalysisService {

    private final PracticeRepository practiceRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final AiAnalysisTxService aiAnalysisTxService;
    private final AiAnalysisResultRepository aiAnalysisResultRepository;
    private final WebClient webClient;
    private final ScriptWordRepository scriptWordRepository;
    private final TargetMetricCalculator targetMetricCalculator;
    private final ScriptTxService scriptTxService; // [STEP-B] 낭독기호 비동기 DB 갱신용

    public AiAnalysisServiceImpl(
            PracticeRepository practiceRepository,
            AnalysisResultRepository analysisResultRepository,
            AiAnalysisTxService aiAnalysisTxService,
            AiAnalysisResultRepository aiAnalysisResultRepository,
            @Qualifier("webClient") WebClient webClient,
            ScriptWordRepository scriptWordRepository,
            TargetMetricCalculator targetMetricCalculator,
            ScriptTxService scriptTxService
    ) {
        this.practiceRepository = practiceRepository;
        this.analysisResultRepository = analysisResultRepository;
        this.aiAnalysisTxService = aiAnalysisTxService;
        this.aiAnalysisResultRepository = aiAnalysisResultRepository;
        this.webClient = webClient;
        this.scriptWordRepository = scriptWordRepository;
        this.targetMetricCalculator = targetMetricCalculator;
        this.scriptTxService = scriptTxService;
    }

    // 비동기 분석 및 결과 저장 로직 구현 
    @Override
    @Async
    public void processAnalysisAsync(Long practiceId, String audioUrl) {
        try {
            // DB 조회는 여기서 완료되고 커넥션은 즉시 반납됩니다.
            PracticeRecord record = practiceRepository.findByIdWithDetails(practiceId)
                    .orElseThrow(() -> new CustomException(PracticeErrorCode.PRACTICE_NOT_FOUND));

            // 외부 서버 호출 시에는 트랜잭션(커넥션)을 물고 있지 않습니다.
            PythonAnalysisRes pythonData = requestPythonAnalysis(record, audioUrl);

            if (pythonData != null) {
                // 결과 저장은 내부에서 REQUIRES_NEW 트랜잭션으로 처리됩니다.
                aiAnalysisTxService.saveResults(practiceId, pythonData);
            }
        } catch (Exception e) {
            log.error("연습 분석 중 오류 발생 - 연습 ID: {}, 원인: ", practiceId, e);
            aiAnalysisTxService.handleAnalysisFailure(practiceId);
        }
    }


    // 파이썬 분석 서버와의 실제 통신 로직 완성 구현
    private PythonAnalysisRes requestPythonAnalysis(PracticeRecord record, String audioUrl) {
        // 파이썬 서버가 분석에 필요한 모든 데이터를 Map에 담음
        Map<String, Object> body = new HashMap<>();
        body.put("practiceId", record.getId());
        body.put("audioUrl", audioUrl);
        body.put("content", record.getScript().getContent());
        body.put("markedContent", record.getScript().getMarkedContent()); // 낭독 기호 대본
        body.put("scriptWords", getScriptWordsPayload(record.getScript()));
        body.put("audienceType", record.getAudienceType().toString()); // Enum -> String
        body.put("audienceUnderstanding", record.getAudienceUnderstanding().toString());
        body.put("speechInformation", record.getSpeechInformation().toString());
        SpeechStyle speechStyle = record.getSpeechStyle();
        body.put("styleType", speechStyle != null ? speechStyle.getStyleType() : null); // 스타일 정보
        // [STEP 4-B] gender 전달 — STEP 1에서 AnalyzeRequest schema에 추가했으나 Java에서 미전달이었던 항목 복구
        body.put("gender", record.getUser().getGender() != null ? record.getUser().getGender().name() : null);
        body.put("targetMetrics", buildTargetMetricsPayload(record));

        // 파이썬 서버의 /analyze 엔드포인트 호출
        return webClient.post()
                .uri("/analyze")
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(responseBody -> new IllegalStateException(String.format(
                                "Python analysis request failed: status=%s, body=%s",
                                response.statusCode(),
                                responseBody
                        ))))
                .bodyToMono(PythonAnalysisRes.class)
                .block(); // 비동기 스레드 내에서 호출되므로 block() 사용 가능
    }

    private Map<String, Object> buildTargetMetricsPayload(PracticeRecord record) {
        SpeechStyle speechStyle = record.getSpeechStyle();
        if (speechStyle == null) {
            return Map.of();
        }

        TargetMetricResult targetMetricResult = targetMetricCalculator.calculate(
                record.getUser(),
                speechStyle.getStyleType(),
                record.getAudienceUnderstanding(),
                record.getAudienceType()
        );

        Map<String, Object> payload = new HashMap<>();
        payload.put("targetWpm", targetMetricResult.targetWpm());
        payload.put("targetPitch", targetMetricResult.targetPitch());
        payload.put("targetIntensity", targetMetricResult.targetIntensity());
        payload.put("targetZcr", targetMetricResult.targetZcr());
        payload.put("targetPauseRatio", targetMetricResult.targetPauseRatio());
        return payload;
    }

    // 파이썬 분석용 대본 단어 목록 생성 구현
    private List<Map<String, Object>> getScriptWordsPayload(Script script) {
        return scriptWordRepository.findAllByScriptSentenceScriptIdOrderByGlobalWordIndexAsc(script.getId())
                .stream()
                .map(this::toScriptWordPayload)
                .toList();
    }

    // 파이썬 분석용 대본 단어 변환 구현
    private Map<String, Object> toScriptWordPayload(ScriptWord scriptWord) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("scriptWordId", scriptWord.getId());
        payload.put("scriptSentenceId", scriptWord.getScriptSentence().getId());
        payload.put("sentenceIndex", scriptWord.getScriptSentence().getSentenceIndex());
        payload.put("globalWordIndex", scriptWord.getGlobalWordIndex());
        payload.put("sentenceWordIndex", scriptWord.getSentenceWordIndex());
        payload.put("text", scriptWord.getText());
        payload.put("normalizedText", scriptWord.getNormalizedText());
        payload.put("startCharIndex", scriptWord.getStartCharIndex());
        payload.put("endCharIndex", scriptWord.getEndCharIndex());
        return payload;
    }

    // [STEP-B] 낭독기호 비동기 생성 — 대본 저장 직후 백그라운드 실행
    // 흐름: addScript()가 원본 content로 즉시 저장 → 이 메서드가 비동기로 Gemini 호출
    //       → 성공 시 markedContent DB 갱신, 실패 시 원본 유지(사용자 영향 없음)
    @Override
    @Async("markingExecutor")
    public void generateMarkedContentAsync(Long scriptId, String content) {
        try {
            log.info("[STEP-B] 낭독기호 비동기 생성 시작 - scriptId: {}, contentLen: {}", scriptId, content.length());
            String markedContent = generateMarkedContent(content);
            if (markedContent != null && !markedContent.isBlank()) {
                scriptTxService.updateMarkedContent(scriptId, markedContent);
                log.info("[STEP-B] 낭독기호 비동기 갱신 완료 - scriptId: {}", scriptId);
            } else {
                log.warn("[STEP-B] 낭독기호 비동기 결과 없음, 원본 유지 - scriptId: {}", scriptId);
            }
        } catch (Exception e) {
            log.error("[STEP-B] 낭독기호 비동기 생성 실패, 원본 유지 - scriptId: {}", scriptId, e);
        }
    }

    // [STEP-B] 파이썬 로컬 마킹 엔진 호출 — 3000ms 타임아웃 + 원문 폴백
    // Python이 KoNLPy+Regex 로컬 엔진을 사용하므로 평균 < 50ms.
    // KoNLPy JVM 첫 기동(워밍업 전 요청)은 1~3초 소요될 수 있어 3000ms로 설정.
    // 3000ms 내에 응답 없으면 원문(content)을 즉시 반환 → 사용자 블로킹 없음.
    @Override
    public String generateMarkedContent(String content) {
        Map<String, Object> body = new HashMap<>();
        body.put("content", content);

        log.info("[STEP-B] Python /scripts/mark 호출 시작 - contentLen: {}", content.length());
        try {
            Map<String, Object> response = webClient.post()
                    .uri("/scripts/mark")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofMillis(3000))         // 3000ms 가드 (KoNLPy JVM 첫 기동 대비)
                    .onErrorResume(e -> {
                        log.warn("[STEP-B] 마킹 요청 오류: {}", e.getMessage());
                        return reactor.core.publisher.Mono.empty();
                    })
                    .block();

            String marked = response != null ? (String) response.get("markedContent") : null;
            if (marked == null || marked.isBlank()) {
                log.warn("[STEP-B] 마킹 결과 없음 또는 타임아웃, 원문 반환 - contentLen: {}", content.length());
                return content;  // 원문 폴백
            }
            log.info("[STEP-B] 마킹 완료 - markedLen: {}", marked.length());
            return marked;
        } catch (Exception e) {
            log.error("[STEP-B] 마킹 요청 실패, 원문 반환", e);
            return content;  // 원문 폴백
        }
    }
}

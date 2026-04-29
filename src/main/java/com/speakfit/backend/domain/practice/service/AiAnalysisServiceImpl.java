package com.speakfit.backend.domain.practice.service;

import com.speakfit.backend.domain.practice.dto.res.PythonAnalysisRes;
import com.speakfit.backend.domain.practice.entity.PracticeRecord;
import com.speakfit.backend.domain.practice.exception.PracticeErrorCode;
import com.speakfit.backend.domain.practice.repository.AiAnalysisResultRepository;
import com.speakfit.backend.domain.practice.repository.AnalysisResultRepository;
import com.speakfit.backend.domain.practice.repository.PracticeRepository;
import com.speakfit.backend.domain.script.entity.Script;
import com.speakfit.backend.domain.script.entity.ScriptWord;
import com.speakfit.backend.domain.script.repository.ScriptWordRepository;
import com.speakfit.backend.domain.style.entity.SpeechStyle;
import com.speakfit.backend.global.apiPayload.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiAnalysisServiceImpl implements AiAnalysisService {

    private final PracticeRepository practiceRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final AiAnalysisTxService aiAnalysisTxService;
    private final AiAnalysisResultRepository aiAnalysisResultRepository;
    private final WebClient webClient;
    private final ScriptWordRepository scriptWordRepository;

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
        body.put("styleType", record.getSpeechStyle().getStyleType()); // 스타일 정보

        // 파이썬 서버의 /analyze 엔드포인트 호출
        return webClient.post()
                .uri("/analyze")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(PythonAnalysisRes.class)
                .block(); // 비동기 스레드 내에서 호출되므로 block() 사용 가능
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

    // 파이썬 서버에 대본 기호 생성(Marking) 요청 구현
    @Override
    public String generateMarkedContent(String content) {
        Map<String, Object> body = new HashMap<>();
        body.put("content", content);

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
            log.error("AI 기호 대본 생성 실패", e);
            return null;
        }
    }
}

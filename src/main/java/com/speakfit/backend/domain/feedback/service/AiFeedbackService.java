package com.speakfit.backend.domain.feedback.service;

import com.speakfit.backend.domain.feedback.repository.FeedbackRepository;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;
import java.time.LocalDate;
import java.time.Duration;

// AI 피드백 처리를 위한 서비스 클래스입니다.
@Service
@RequiredArgsConstructor
@Slf4j
public class AiFeedbackService {
    private final FeedbackRepository feedbackRepository;
    private final WebClient webClient;
    private final PlatformTransactionManager transactionManager;

    // 비동기로 피드백을 처리하는 메소드입니다.
    // feedbackExecutor 스레드 위에서 block()으로 호출하여 try/catch가 실제 예외를 잡도록 통일
    // 사용자 요청 스레드와 완전히 분리된 별도 스레드에서 실행됨
    @Async("feedbackExecutor")
    public void processFeedbackAsync(Long feedbackId, Double w, Double h, Double d, Double z, Double p, LocalDate start, LocalDate end) {
        try {
            PythonFeedbackReq req = PythonFeedbackReq.builder()
                    .feedbackId(feedbackId)
                    .avgWpm(w)
                    .avgPitch(h)
                    .avgIntensity(d)
                    .avgZcr(z)
                    .pauseRatio(p)
                    .startDate(start.toString())
                    .endDate(end.toString())
                    .build();

            PythonFeedbackRes res = webClient.post()
                    .uri("/feedback/summary")
                    .bodyValue(req)
                    .retrieve()
                    .bodyToMono(PythonFeedbackRes.class)
                    .timeout(Duration.ofSeconds(60))
                    // 일시적 AI 서버 장애 대비: 2초 간격으로 최대 2회 재시도
                    .retryWhen(Retry.backoff(2, Duration.ofSeconds(2))
                            .doBeforeRetry(signal ->
                                    log.warn("AI 피드백 재시도 feedbackId={}, attempt={}",
                                            feedbackId, signal.totalRetries() + 1)))
                    .block(); // @Async 스레드에서 블로킹 → try/catch가 실제 예외를 직접 포착

            updateFeedbackResult(feedbackId, res);
        } catch (Exception e) {
            log.error("AI 피드백 처리 실패 feedbackId={}", feedbackId, e);
            failFeedbackResult(feedbackId);
        }
    }

    // 피드백 결과를 업데이트합니다.
    public void updateFeedbackResult(Long feedbackId, PythonFeedbackRes res) {
        try {
            // 유효성 검사 수행 (실패 시 예외 발생)
            validateResponse(res);

            new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                    feedbackRepository.findById(feedbackId).ifPresent(f ->
                            f.completeAnalysis(
                                    res.mostSimilarStyle, res.matchingRate, res.styleDescription,
                                    res.positiveTitle, res.positiveDescription, res.improvementTitle,
                                    res.improvementDescription, res.guideSummary, res.guideNextStep
                            )
                    )
            );
        } catch (Exception e) {
            log.error("AI Feedback validation failed: ", e);
            // 유효하지 않은 응답이 오면 FAILED 상태로 업데이트
            failFeedbackResult(feedbackId);
        }
    }

    // AI 응답 검증 메서드
    private void validateResponse(PythonFeedbackRes res) {
        if (res == null
                || res.getMatchingRate() == null
                || res.getMatchingRate() < 0
                || res.getMatchingRate() > 100
                || res.getMostSimilarStyle() == null || res.getMostSimilarStyle().trim().isEmpty()
                || res.getStyleDescription() == null || res.getStyleDescription().trim().isEmpty()
                || res.getPositiveTitle() == null || res.getPositiveTitle().trim().isEmpty()
                || res.getPositiveDescription() == null || res.getPositiveDescription().trim().isEmpty()
                || res.getImprovementTitle() == null || res.getImprovementTitle().trim().isEmpty()
                || res.getImprovementDescription() == null || res.getImprovementDescription().trim().isEmpty()
                || res.getGuideSummary() == null || res.getGuideSummary().trim().isEmpty()
                || res.getGuideNextStep() == null || res.getGuideNextStep().trim().isEmpty()) {

            throw new IllegalArgumentException("Invalid AI feedback payload");
        }
    }

    // 피드백 처리가 실패했을 때 상태를 변경합니다.
    public void failFeedbackResult(Long feedbackId) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                feedbackRepository.findById(feedbackId).ifPresent(f -> {
                    f.failAnalysis();
                    feedbackRepository.save(f);
                })
        );
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class PythonFeedbackReq {
        private Long feedbackId;
        private Double avgWpm;
        private Double avgPitch;
        private Double avgIntensity;
        private Double avgZcr;
        private Double pauseRatio;
        private String startDate;
        private String endDate;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PythonFeedbackRes {
        private String mostSimilarStyle;
        private String styleDescription;
        private String positiveTitle;
        private String positiveDescription;
        private String improvementTitle;
        private String improvementDescription;
        private String guideSummary;
        private String guideNextStep;
        private Integer matchingRate;
    }
}
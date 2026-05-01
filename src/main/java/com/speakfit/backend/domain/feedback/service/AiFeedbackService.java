package com.speakfit.backend.domain.feedback.service;

import com.speakfit.backend.domain.feedback.repository.FeedbackRepository;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.reactive.function.client.WebClient;
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
    @Async
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

            webClient.post()
                    .uri("/feedback/summary")
                    .bodyValue(req)
                    .retrieve()
                    .bodyToMono(PythonFeedbackRes.class)
                    .timeout(Duration.ofSeconds(60))
                    .subscribe(
                            // 메서드 참조 대신 람다 표현식으로 feedbackId를 넘기도록 수정
                            res -> updateFeedbackResult(feedbackId, res),
                            err -> {
                                log.error("AI Error: ", err);
                                // AI 서비스 호출 중 에러 발생 시 실패 상태로 변경
                                failFeedbackResult(feedbackId);
                            }
                    );
        } catch (Exception e) {
            log.error("Internal Error: ", e);
            // 예외 발생 시 실패 상태로 변경
            failFeedbackResult(feedbackId);
        }
    }

    // 피드백 결과를 업데이트합니다.
    public void updateFeedbackResult(Long feedbackId, PythonFeedbackRes res) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                feedbackRepository.findById(feedbackId).ifPresent(f ->
                        f.completeAnalysis(
                                res.mostSimilarStyle, res.matchingRate, res.styleDescription,
                                res.positiveTitle, res.positiveDescription, res.improvementTitle,
                                res.improvementDescription, res.guideSummary, res.guideNextStep
                        )
                )
        );
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
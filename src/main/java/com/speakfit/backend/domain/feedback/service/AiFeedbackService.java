package com.speakfit.backend.domain.feedback.service;

import com.speakfit.backend.domain.feedback.enums.FeedbackStatus;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class AiFeedbackService {
    private final FeedbackRepository feedbackRepository;
    private final WebClient webClient;
    private final PlatformTransactionManager transactionManager;

    @Async
    public void processFeedbackAsync(Long feedbackId, Double w, Double h, Double d, Double z, Double p, LocalDate start, LocalDate end) {
        try {
            PythonFeedbackReq req = PythonFeedbackReq.builder().feedbackId(feedbackId).avgWpm(w).avgPitch(h).avgIntensity(d).avgZcr(z).pauseRatio(p).startDate(start.toString()).endDate(end.toString()).build();
            webClient.post().uri("/feedback/summary").bodyValue(req).retrieve().bodyToMono(PythonFeedbackRes.class).timeout(Duration.ofSeconds(60))
                    .subscribe(res -> updateFeedbackResult(feedbackId, res), err -> log.error("AI Error: ", err));
        } catch (Exception e) { log.error("Internal Error: ", e); }
    }

    public void updateFeedbackResult(Long feedbackId, PythonFeedbackRes res) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            feedbackRepository.findById(feedbackId).ifPresent(f -> f.completeAnalysis(res.mostSimilarStyle, res.matchingRate, res.styleDescription, res.positiveTitle, res.positiveDescription, res.improvementTitle, res.improvementDescription, res.guideSummary, res.guideNextStep));
        });
    }

    @Getter @Builder @AllArgsConstructor
    static class PythonFeedbackReq { private Long feedbackId; private Double avgWpm, avgPitch, avgIntensity, avgZcr, pauseRatio; private String startDate, endDate; }
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    static class PythonFeedbackRes { private String mostSimilarStyle, styleDescription, positiveTitle, positiveDescription, improvementTitle, improvementDescription, guideSummary, guideNextStep; private Integer matchingRate; }
}
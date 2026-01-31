package com.speakfit.backend.domain.feedback.service;


import com.speakfit.backend.domain.feedback.enums.FeedbackStatus;
import com.speakfit.backend.domain.feedback.repository.FeedbackRepository;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.time.Duration;
@Service
@RequiredArgsConstructor
@Slf4j
public class AiFeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final WebClient webClient;


    // 파이썬 AI 서버에 피드백 분석을 비동기로 요청
    @Async // 별도 스레드에서 비동기 처리
    @Transactional
    public void processFeedbackAsync(Long feedbackId, Double avgWpm, Double avgPitch,
                                     Double avgIntensity, Double avgZcr, Double pauseRatio,
                                     LocalDate startDate, LocalDate endDate) {

        log.info("AI 종합 피드백 요청 시작 - ID: {}", feedbackId);

        try {
            // 파이썬 서버 요청 DTO 생성
            PythonFeedbackReq req = PythonFeedbackReq.builder()
                    .feedbackId(feedbackId)
                    .avgWpm(avgWpm)
                    .avgPitch(avgPitch)
                    .avgIntensity(avgIntensity)
                    .avgZcr(avgZcr)
                    .pauseRatio(pauseRatio)
                    .startDate(startDate.toString())
                    .endDate(endDate.toString())
                    .build();

            // 파이썬 서버 호출 및 비동기 응답 처리
            webClient.post()
                    .uri("/feedback/summary")
                    .bodyValue(req)
                    .retrieve()
                    .bodyToMono(PythonFeedbackRes.class)
                    .timeout(Duration.ofSeconds(60))
                    .subscribe(response -> {
                        // 성공 시 DB 업데이트
                        updateFeedbackResult(feedbackId, response.getAiFeedback(), FeedbackStatus.COMPLETED);
                    }, error -> {
                        // 통신 실패 시 처리
                        log.error("AI 서버 통신 오류: ", error);
                        updateFeedbackResult(feedbackId, "AI 서버 통신 실패", FeedbackStatus.FAILED);
                    });

        } catch (Exception e) {
            log.error("내부 오류 발생: ", e);
            updateFeedbackResult(feedbackId, "내부 시스템 오류", FeedbackStatus.FAILED);
        }
    }

    // 피드백 결과 업데이트 메소드

    @Transactional
    public void updateFeedbackResult(Long feedbackId, String content, FeedbackStatus status) {
        // 데이터가 없을 경우 경고 로그를 남기도록 개선
        feedbackRepository.findById(feedbackId).ifPresentOrElse(feedback -> {
            feedback.updateFeedback(content, status);
            feedbackRepository.save(feedback);
            log.info("피드백 상태 업데이트 완료: {} -> {}", feedbackId, status);
        }, () -> log.warn("피드백을 찾을 수 없습니다. ID: {}", feedbackId));
    }


    // 파이썬 서버 통신용 내부 DTO
    @Getter @Builder @AllArgsConstructor
    static class PythonFeedbackReq {
        private Long feedbackId;
        private Double avgWpm;
        private Double avgPitch;
        private Double avgIntensity;
        private Double avgZcr;
        private Double pauseRatio;
        private String startDate;
        private String endDate;
    }

    @Getter @AllArgsConstructor @NoArgsConstructor
    static class PythonFeedbackRes {
        private String aiFeedback;
    }
}
package com.speakfit.backend.domain.feedback.service;

import com.speakfit.backend.domain.feedback.dto.req.GenerateFeedbackReq;
import com.speakfit.backend.domain.feedback.dto.res.GenerateFeedbackRes;
import com.speakfit.backend.domain.feedback.dto.res.GetFeedbackDetailRes;
import com.speakfit.backend.domain.feedback.entity.Feedback;
import com.speakfit.backend.domain.feedback.enums.FeedbackStatus;
import com.speakfit.backend.domain.feedback.exception.FeedbackErrorCode;
import com.speakfit.backend.domain.feedback.repository.FeedbackRepository;
import com.speakfit.backend.domain.practice.entity.AnalysisResult;
import com.speakfit.backend.domain.practice.entity.PracticeRecord;
import com.speakfit.backend.domain.practice.enums.Status;
import com.speakfit.backend.domain.practice.exception.PracticeErrorCode;
import com.speakfit.backend.domain.practice.repository.AnalysisResultRepository;
import com.speakfit.backend.domain.practice.repository.PracticeRepository;
import com.speakfit.backend.domain.script.exception.ScriptErrorCode;
import com.speakfit.backend.domain.user.entity.User;
import com.speakfit.backend.domain.user.repository.UserRepository;
import com.speakfit.backend.global.apiPayload.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeedbackServiceImpl implements FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final UserRepository userRepository;
    private final PracticeRepository practiceRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final AiFeedbackService aiFeedbackService;

    // 내부 계산용 레코드 (중복 제거용)
    private record CalculatedMetrics(double w, double d, double p, double z, double h) {}

    @Override
    @Transactional
    public GenerateFeedbackRes generateFeedback(GenerateFeedbackReq.Request req, Long userId) {

        // 1. 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ScriptErrorCode.SCRIPT_USER_NOT_FOUND));

        if (req.getEndDate().isBefore(req.getStartDate())) {
            throw new CustomException(FeedbackErrorCode.FEEDBACK_INVALID_DATE_RANGE);
        }
        // 2. 날짜 범위 설정 (00:00:00 ~ 23:59:59)
        LocalDateTime startDateTime = req.getStartDate().atStartOfDay();
        LocalDateTime endDateTime = req.getEndDate().atTime(LocalTime.MAX);

        // 3. 해당 기간의 분석 완료된 연습 기록 조회
        List<PracticeRecord> records = practiceRepository.findAllByUserAndStatusAndCreatedAtBetween(
                user, Status.ANALYZED, startDateTime, endDateTime
        );

        if (records.isEmpty()) {
            throw new CustomException(PracticeErrorCode.PRACTICE_NOT_FOUND);
        }

        // 4. 5대 지표 평균 계산
        CalculatedMetrics metrics = getCalculatedMetrics(records);

        // 5. 피드백 엔티티 생성 및 저장
        Feedback feedback = Feedback.builder()
                .user(user)
                .startDate(req.getStartDate())
                .endDate(req.getEndDate())
                .status(FeedbackStatus.GENERATING)
                .build();

        Feedback savedFeedback = feedbackRepository.save(feedback);

        // 6. 트랜잭션 커밋 이후에만 비동기 AI 분석 요청
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                aiFeedbackService.processFeedbackAsync(
                        savedFeedback.getId(), metrics.w(), metrics.h(), metrics.d(),
                        metrics.z(), metrics.p(), req.getStartDate(), req.getEndDate()
                );
            }
        });

        // 7. 결과 반환
        return GenerateFeedbackRes.builder()
                .feedbackId(savedFeedback.getId())
                .status(savedFeedback.getStatus().toString())
                .message("종합 피드백 생성이 요청되었습니다.")
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public GetFeedbackDetailRes getSummaryFeedbackDetail(Long feedbackId, Long userId) {

        // 1. 피드백 조회 및 권한 확인
        Feedback feedback = feedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new CustomException(FeedbackErrorCode.FEEDBACK_NOT_FOUND));

        if (!feedback.getUser().getId().equals(userId)) {
            throw new CustomException(FeedbackErrorCode.FEEDBACK_ACCESS_DENIED);
        }

        // 2. 분석 중인 경우 조기 반환
        if (feedback.getStatus() != FeedbackStatus.COMPLETED) {
            return GetFeedbackDetailRes.builder()
                    .id(feedback.getId())
                    .status(feedback.getStatus().toString())
                    .message("AI가 최근 연습 기록들을 종합 분석하고 있습니다.")
                    .build();
        }

        // 기간 설정 (이번 주 vs 지난 주)]
        LocalDateTime thisStart = feedback.getStartDate().atStartOfDay();
        LocalDateTime thisEnd = feedback.getEndDate().atTime(LocalTime.MAX);
        long days = java.time.temporal.ChronoUnit.DAYS.between(feedback.getStartDate(), feedback.getEndDate()) + 1;
        LocalDateTime prevStart = thisStart.minusDays(days);
        LocalDateTime prevEnd = thisEnd.minusDays(days);

        List<PracticeRecord> curRecords = practiceRepository.findAllByUserAndStatusAndCreatedAtBetween(feedback.getUser(), Status.ANALYZED, thisStart, thisEnd);
        List<PracticeRecord> prevRecords = practiceRepository.findAllByUserAndStatusAndCreatedAtBetween(feedback.getUser(), Status.ANALYZED, prevStart, prevEnd);

        // 5대 지표 수치 계산 (중복 로직 제거용)
        CalculatedMetrics cur = getCalculatedMetrics(curRecords);
        CalculatedMetrics prev = getCalculatedMetrics(prevRecords);

        // DB에서 실제 분석 텍스트 조회
        return GetFeedbackDetailRes.builder()
                .id(feedback.getId())
                .status(feedback.getStatus().toString())
                .startDate(feedback.getStartDate().toString())
                .endDate(feedback.getEndDate().toString())
                .userAverageMetrics(GetFeedbackDetailRes.UserAverageMetrics.builder()
                        .avgSpeed((int)cur.w() + " wpm").avgDB((int)cur.d() + " dB")
                        .totalPauses((int)cur.p() + " 회").avgZCR((int)cur.z() + " %").avgHz((int)cur.h() + " Hz").build())
                .styleMatching(GetFeedbackDetailRes.StyleMatching.builder()
                        .mostSimilarStyle(feedback.getMostSimilarStyle()) // DB 필드 반영]
                        .matchingRate(feedback.getMatchingRate())
                        .description(feedback.getStyleDescription()).build())
                .growthTrend(GetFeedbackDetailRes.GrowthTrend.builder()
                        .speed(createMetricDiff(cur.w(), prev.w(), "wpm")).db(createMetricDiff(cur.d(), prev.d(), "dB"))
                        .pause(createMetricDiff(cur.p(), prev.p(), "회")).zcr(createMetricDiff(cur.z(), prev.z(), "%"))
                        .hz(createMetricDiff(cur.h(), prev.h(), "Hz")).build())
                .aiReport(GetFeedbackDetailRes.AiReport.builder()
                        .positiveFeedback(GetFeedbackDetailRes.FeedbackDetail.builder()
                                .title(feedback.getPositiveTitle()) // DB 필드 반영]
                                .description(feedback.getPositiveDescription()).build())
                        .improvementFeedback(GetFeedbackDetailRes.FeedbackDetail.builder()
                                .title(feedback.getImprovementTitle())
                                .description(feedback.getImprovementDescription()).build()).build())
                .practiceGuide(GetFeedbackDetailRes.PracticeGuide.builder()
                        .targetMetrics(Arrays.asList(feedback.getGuideSummary().split(","))) // 쉼표 구분 지표 추출]
                        .summary(feedback.getGuideSummary())
                        .nextStep(feedback.getGuideNextStep()).build())
                .build();
    }

    // 5대 지표 계산 통합 헬퍼
    private CalculatedMetrics getCalculatedMetrics(List<PracticeRecord> records) {
        double w = calculateAverage(records, AnalysisResult::getAvgWpm);
        double d = calculateAverage(records, AnalysisResult::getAvgIntensity);
        double p = calculateAverage(records, r -> r.getPauseCount() != null ? r.getPauseCount().doubleValue() : 0.0);
        double z = calculateAverage(records, AnalysisResult::getAvgZcr) * 100;
        double h = calculateAverage(records, AnalysisResult::getAvgPitch);
        return new CalculatedMetrics(w, d, p, z, h);
    }

    private GetFeedbackDetailRes.MetricDiff createMetricDiff(double cur, double prev, String unit) {
        double diff = cur - prev;
        String diffStr = (diff >= 0 ? "+ " : "- ") + Math.abs((int)diff) + unit;
        return GetFeedbackDetailRes.MetricDiff.builder().current(cur).previous(prev).diff(diffStr).build();
    }

    private double calculateAverage(List<PracticeRecord> records, java.util.function.Function<AnalysisResult, Double> mapper) {
        return records.stream()
                .map(record -> analysisResultRepository.findByPracticeRecord(record)
                        .orElseThrow(() -> new CustomException(PracticeErrorCode.PRACTICE_NOT_FOUND)))
                .map(mapper).filter(Objects::nonNull).mapToDouble(Double::doubleValue).average().orElse(0.0);
    }
}
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
import com.speakfit.backend.domain.practice.enums.PracticeStatus;
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


    // 피드백 생성 요청 서비스 구현
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
                user, PracticeStatus.ANALYZED, startDateTime, endDateTime
        );

        if (records.isEmpty()) {
            throw new CustomException(PracticeErrorCode.PRACTICE_NOT_FOUND);
        }

        // 4. 지표별 평균 데이터 계산 (헬퍼 메소드 활용)
        double avgWpm = calculateAverage(records, AnalysisResult::getAvgWpm);
        double avgPitch = calculateAverage(records, AnalysisResult::getAvgPitch);
        double avgIntensity = calculateAverage(records, AnalysisResult::getAvgIntensity);
        double avgZcr = calculateAverage(records, AnalysisResult::getAvgZcr);
        double pauseRatio = calculateAverage(records, AnalysisResult::getPauseRatio);

        // 5. 피드백 엔티티 생성 및 저장
        Feedback feedback = Feedback.builder()
                .user(user)
                .startDate(req.getStartDate())
                .endDate(req.getEndDate())
                .status(FeedbackStatus.GENERATING)
                .build();

        Feedback savedFeedback = feedbackRepository.save(feedback);

        // 6. 트랜잭션 커밋 이후에만 비동기 AI 분석 요청 (Race Condition 방지)
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                aiFeedbackService.processFeedbackAsync(
                        savedFeedback.getId(), avgWpm, avgPitch, avgIntensity,
                        avgZcr, pauseRatio, req.getStartDate(), req.getEndDate()
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

    // 내부 헬퍼 메소드: 스트림 중복 로직 제거
    // Double unboxing 시 NPE 방지를 위해 필터링 추가
    private double calculateAverage(List<PracticeRecord> records,
                                    java.util.function.Function<AnalysisResult, Double> mapper) {
        return records.stream()
                .map(record -> analysisResultRepository.findByPracticeRecord(record)
                        .orElseThrow(() -> new CustomException(PracticeErrorCode.PRACTICE_NOT_FOUND)))
                .map(mapper)
                .filter(Objects::nonNull) // null 값 필터링
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
    }

    @Override
    @Transactional(readOnly = true)
    public GetFeedbackDetailRes getFeedbackDetail(Long feedbackId, Long userId) {

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

        // 3. 기간 설정 (이번 주 vs 지난 주)
        LocalDateTime thisWeekStart = feedback.getStartDate().atStartOfDay();
        LocalDateTime thisWeekEnd = feedback.getEndDate().atTime(LocalTime.MAX);

        // 지난 주 계산: 이번 주 기간만큼 뒤로 이동
        long days = java.time.temporal.ChronoUnit.DAYS.between(feedback.getStartDate(), feedback.getEndDate()) + 1;
        LocalDateTime lastWeekStart = thisWeekStart.minusDays(days);
        LocalDateTime lastWeekEnd = thisWeekEnd.minusDays(days);

        // 4. 이번 주 데이터 조회
        List<PracticeRecord> thisWeekRecords = practiceRepository.findAllByUserAndStatusAndCreatedAtBetween(
                feedback.getUser(), PracticeStatus.ANALYZED, thisWeekStart, thisWeekEnd
        );

        // 5. 지난 주 데이터 조회 (비교용)
        List<PracticeRecord> lastWeekRecords = practiceRepository.findAllByUserAndStatusAndCreatedAtBetween(
                feedback.getUser(), PracticeStatus.ANALYZED, lastWeekStart, lastWeekEnd
        );

        // 6. 평균값 계산 (Helper 메소드 활용)
        GetFeedbackDetailRes.Metrics thisWeekMetrics = calculateMetrics(thisWeekRecords);
        GetFeedbackDetailRes.Metrics lastWeekMetrics = calculateMetrics(lastWeekRecords);

        // 7. 변화량(Highlights) 계산: (이번 주 - 지난 주)
        GetFeedbackDetailRes.AnalysisHighlights highlights = GetFeedbackDetailRes.AnalysisHighlights.builder()
                .wpmDiff(thisWeekMetrics.getAvgWpm() - lastWeekMetrics.getAvgWpm())
                .pauseCountDiff(calculateAverage(thisWeekRecords, result -> (double) result.getPauseCount())
                        - calculateAverage(lastWeekRecords, result -> (double) result.getPauseCount()))
                .intensityDiff(thisWeekMetrics.getAvgIntensity() - lastWeekMetrics.getAvgIntensity())
                .pitchDiff(thisWeekMetrics.getAvgPitch() - lastWeekMetrics.getAvgPitch())
                .build();

        // 8. 최종 응답 생성
        return GetFeedbackDetailRes.builder()
                .id(feedback.getId())
                .status(feedback.getStatus().toString())
                .startDate(feedback.getStartDate().toString())
                .endDate(feedback.getEndDate().toString())
                .comparisonData(GetFeedbackDetailRes.ComparisonData.builder()
                        .thisWeek(thisWeekMetrics)
                        .lastWeek(lastWeekMetrics)
                        .build())
                .analysisHighlights(highlights)
                .aiFeedback(feedback.getAiFeedback())
                .build();
    }

    // 연습 기록 목록을 바탕으로 메트릭 일괄 계산
    private GetFeedbackDetailRes.Metrics calculateMetrics(List<PracticeRecord> records) {
        if (records.isEmpty()) {
            return GetFeedbackDetailRes.Metrics.builder()
                    .avgWpm(0.0).avgPitch(0.0).avgIntensity(0.0).pauseRatio(0.0)
                    .build();
        }
        return GetFeedbackDetailRes.Metrics.builder()
                .avgWpm(calculateAverage(records, AnalysisResult::getAvgWpm))
                .avgPitch(calculateAverage(records, AnalysisResult::getAvgPitch))
                .avgIntensity(calculateAverage(records, AnalysisResult::getAvgIntensity))
                .pauseRatio(calculateAverage(records, AnalysisResult::getPauseRatio))
                .build();
    }
}
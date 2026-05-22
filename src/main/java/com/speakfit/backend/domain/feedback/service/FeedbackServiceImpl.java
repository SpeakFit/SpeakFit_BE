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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeedbackServiceImpl implements FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final UserRepository userRepository;
    private final PracticeRepository practiceRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final AiFeedbackService aiFeedbackService;

    // 내부 계산용 레코드
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

        // 3. 기간 설정 및 데이터 일괄 조회 (N+1 방지)
        LocalDateTime thisStart = feedback.getStartDate().atStartOfDay();
        LocalDateTime thisEnd = feedback.getEndDate().atTime(LocalTime.MAX);

        List<PracticeRecord> curRecords = practiceRepository.findAllByUserAndStatusAndCreatedAtBetween(
                feedback.getUser(), Status.ANALYZED, thisStart, thisEnd
        );

        // 4. 대시보드 상단에 띄울 '현재 피드백 기간 내의 전체 평균 스펙' 연산
        CalculatedMetrics curSummary = getCalculatedMetrics(curRecords);

        // 5. 물리 DB 스키마 규격 매핑 기반 날짜별 추이 리스트 빌드업 로직
        List<AnalysisResult> analysisResults = analysisResultRepository.findByPracticeRecordIn(curRecords);

        // 날짜별(LocalDate) 그룹핑
        Map<LocalDate, List<AnalysisResult>> groupedByDate = analysisResults.stream()
                .collect(Collectors.groupingBy(result -> result.getPracticeRecord().getCreatedAt().toLocalDate()));

        List<GetFeedbackDetailRes.TrendPoint> speedTrends = new ArrayList<>();
        List<GetFeedbackDetailRes.TrendPoint> dbTrends = new ArrayList<>();
        List<GetFeedbackDetailRes.TrendPoint> pauseTrends = new ArrayList<>();
        List<GetFeedbackDetailRes.TrendPoint> zcrTrends = new ArrayList<>();
        List<GetFeedbackDetailRes.TrendPoint> hzTrends = new ArrayList<>();

        // 날짜 오름차순으로 정렬하여 각 지표 배열 조립 (실제 물리 DB 찐 컬럼 바인딩)
        groupedByDate.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    String dateStr = entry.getKey().toString();
                    List<AnalysisResult> list = entry.getValue();

                    // 💡 실제 DB의 물리 명세서 컬럼 매핑 함수 매칭
                    double speedVal = list.stream().filter(r -> r.getAvgWpm() != null).mapToDouble(AnalysisResult::getAvgWpm).average().orElse(0.0);
                    double dbVal = list.stream().filter(r -> r.getAvgIntensity() != null).mapToDouble(AnalysisResult::getAvgIntensity).average().orElse(0.0);
                    double pauseVal = list.stream().filter(r -> r.getPauseCount() != null).mapToDouble(AnalysisResult::getPauseCount).average().orElse(0.0);
                    double zcrVal = list.stream().filter(r -> r.getAvgZcr() != null).mapToDouble(AnalysisResult::getAvgZcr).average().orElse(0.0) * 100.0;
                    double hzVal = list.stream().filter(r -> r.getAvgPitch() != null).mapToDouble(AnalysisResult::getAvgPitch).average().orElse(0.0);

                    speedTrends.add(new GetFeedbackDetailRes.TrendPoint(dateStr, Math.round(speedVal * 10.0) / 10.0));
                    dbTrends.add(new GetFeedbackDetailRes.TrendPoint(dateStr, Math.round(dbVal * 10.0) / 10.0));
                    pauseTrends.add(new GetFeedbackDetailRes.TrendPoint(dateStr, Math.round(pauseVal * 10.0) / 10.0));
                    zcrTrends.add(new GetFeedbackDetailRes.TrendPoint(dateStr, Math.round(zcrVal * 10.0) / 10.0));
                    hzTrends.add(new GetFeedbackDetailRes.TrendPoint(dateStr, Math.round(hzVal * 10.0) / 10.0));
                });

        // 6. 개선 대상 지표 목록 파싱 (물리 DB 컬럼 guide_summary 반영)
        List<String> targetMetrics = Collections.emptyList();
        if (feedback.getGuideSummary() != null) {
            targetMetrics = Arrays.asList(feedback.getGuideSummary().split(","));
        }

        // 7. 최종 DTO 매핑 및 빌드 반환 (스네이크 케이스 100% 대응 규격)
        return GetFeedbackDetailRes.builder()
                .id(feedback.getId())
                .status(feedback.getStatus().toString())
                .message("종합 피드백 상세 조회가 완료되었습니다.") // 누락되었던 message 정보 추가
                .startDate(feedback.getStartDate().toString())
                .endDate(feedback.getEndDate().toString())
                .userAverageMetrics(GetFeedbackDetailRes.UserAverageMetrics.builder()
                        .avgSpeed((int) curSummary.w() + " wpm")
                        .avgDB((int) curSummary.d() + " dB")
                        .totalPauses((int) curSummary.p() + " 회")
                        .avgZCR((int) curSummary.z() + " %")
                        .avgHz((int) curSummary.h() + " Hz")
                        .build())
                .styleMatching(GetFeedbackDetailRes.StyleMatching.builder()
                        .mostSimilarStyle(feedback.getMostSimilarStyle())
                        .matchingRate(feedback.getMatchingRate())
                        .description(feedback.getStyleDescription())
                        .build())
                .growthTrend(GetFeedbackDetailRes.GrowthTrend.builder()
                        .speed(speedTrends)
                        .db(dbTrends)
                        .pause(pauseTrends)
                        .zcr(zcrTrends)
                        .hz(hzTrends)
                        .build())
                .aiReport(GetFeedbackDetailRes.AiReport.builder()
                        .positiveFeedback(GetFeedbackDetailRes.FeedbackDetail.builder()
                                .title(feedback.getPositiveTitle())
                                .description(feedback.getPositiveDescription())
                                .build())
                        .improvementFeedback(GetFeedbackDetailRes.FeedbackDetail.builder()
                                .title(feedback.getImprovementTitle())
                                .description(feedback.getImprovementDescription())
                                .build())
                        .build())
                .practiceGuide(GetFeedbackDetailRes.PracticeGuide.builder()
                        .targetMetrics(targetMetrics)
                        .summary(feedback.getGuideSummary())
                        .nextStep(feedback.getGuideNextStep())
                        .build())
                .build();
    }

    private CalculatedMetrics getCalculatedMetrics(List<PracticeRecord> records) {
        if (records.isEmpty()) {
            return new CalculatedMetrics(0.0, 0.0, 0.0, 0.0, 0.0);
        }

        List<AnalysisResult> analysisResults = analysisResultRepository.findByPracticeRecordIn(records);

        double w = calculateForResult(analysisResults, AnalysisResult::getAvgWpm);
        double d = calculateForResult(analysisResults, AnalysisResult::getAvgIntensity);

        double p = analysisResults.stream()
                .map(AnalysisResult::getPauseCount)
                .filter(Objects::nonNull)
                .mapToDouble(Integer::doubleValue)
                .average()
                .orElse(0.0);

        double z = calculateForResult(analysisResults, AnalysisResult::getAvgZcr) * 100;
        double h = calculateForResult(analysisResults, AnalysisResult::getAvgPitch);

        return new CalculatedMetrics(w, d, p, z, h);
    }

    private double calculateForResult(List<AnalysisResult> results, java.util.function.Function<AnalysisResult, Double> mapper) {
        return results.stream()
                .map(mapper)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
    }
}
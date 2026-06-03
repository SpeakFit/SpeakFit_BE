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

        // 4. 5대 지표 생성 시 DB에서 최초 1회 전체 조회 진행
        List<AnalysisResult> analysisResults = analysisResultRepository.findByPracticeRecordIn(records);
        CalculatedMetrics metrics = getCalculatedMetrics(analysisResults);

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

        // 2. 분석 미완료 상태(GENERATING / FAILED) 조기 반환 — 상태별 사용자 안내 메시지 분기
        if (feedback.getStatus() == FeedbackStatus.FAILED) {
            return GetFeedbackDetailRes.builder()
                    .id(feedback.getId())
                    .status(feedback.getStatus().toString())
                    .message("AI 분석 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.")
                    .build();
        }
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

        // 🌟 [★핵심 최적화] 분석 결과 일괄 조회 (중복 쿼리 제거 및 N+1 방지 해결)
        List<AnalysisResult> analysisResults = analysisResultRepository.findByPracticeRecordIn(curRecords);

        // 생성일자 오름차순으로 정렬 (그래프 추이용)
        analysisResults.sort(Comparator.comparing(r -> r.getPracticeRecord().getCreatedAt()));

        // 4. 대시보드 상단에 띄울 전체 평균 스펙 연산 (이미 긁어온 analysisResults 리스트 재사용)
        CalculatedMetrics curSummary = getCalculatedMetrics(analysisResults);

        // 5. 개별 연습 회차별 데이터 조립 (날짜별 그룹화 제거)
        List<GetFeedbackDetailRes.TrendPoint> speedTrends = new ArrayList<>();
        List<GetFeedbackDetailRes.TrendPoint> dbTrends = new ArrayList<>();
        List<GetFeedbackDetailRes.TrendPoint> pauseTrends = new ArrayList<>();
        List<GetFeedbackDetailRes.TrendPoint> zcrTrends = new ArrayList<>();
        List<GetFeedbackDetailRes.TrendPoint> hzTrends = new ArrayList<>();

        int totalPractices = analysisResults.size();
        for (int i = 0; i < totalPractices; i++) {
            AnalysisResult result = analysisResults.get(i);
            
            // 회차 레이블 계산 (가장 최근이 '현회차', 이전은 -1, -2...)
            int diff = i - (totalPractices - 1);
            String sessionLabel = (diff == 0) ? "현회차" : diff + "회차";

            double speedVal = result.getAvgWpm() != null ? result.getAvgWpm() : 0.0;
            double dbVal = result.getAvgIntensity() != null ? result.getAvgIntensity() : 0.0;
            double pauseVal = result.getPauseCount() != null ? result.getPauseCount().doubleValue() : 0.0;
            double zcrVal = result.getAvgZcr() != null ? result.getAvgZcr() * 100.0 : 0.0;
            double hzVal = result.getAvgPitch() != null ? result.getAvgPitch() : 0.0;

            speedTrends.add(new GetFeedbackDetailRes.TrendPoint(sessionLabel, Math.round(speedVal * 10.0) / 10.0));
            dbTrends.add(new GetFeedbackDetailRes.TrendPoint(sessionLabel, Math.round(dbVal * 10.0) / 10.0));
            pauseTrends.add(new GetFeedbackDetailRes.TrendPoint(sessionLabel, Math.round(pauseVal * 10.0) / 10.0));
            zcrTrends.add(new GetFeedbackDetailRes.TrendPoint(sessionLabel, Math.round(zcrVal * 10.0) / 10.0));
            hzTrends.add(new GetFeedbackDetailRes.TrendPoint(sessionLabel, Math.round(hzVal * 10.0) / 10.0));
        }

        // 6. 개선 대상 지표 목록 파싱
        List<String> targetMetrics = Collections.emptyList();
        if (feedback.getGuideSummary() != null) {
            targetMetrics = Arrays.asList(feedback.getGuideSummary().split(","));
        }

        // 7. 최종 DTO 매핑 및 빌드 반환
        return GetFeedbackDetailRes.builder()
                .id(feedback.getId())
                .status(feedback.getStatus().toString())
                .message("종합 피드백 상세 조회가 완료되었습니다.")
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

    // 💡 파라미터 타입을 List<AnalysisResult>로 직접 받도록 변경하여 쿼리 중복 제거
    private CalculatedMetrics getCalculatedMetrics(List<AnalysisResult> analysisResults) {
        if (analysisResults.isEmpty()) {
            return new CalculatedMetrics(0.0, 0.0, 0.0, 0.0, 0.0);
        }

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
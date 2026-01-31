package com.speakfit.backend.domain.feedback.controller;

import com.speakfit.backend.domain.feedback.dto.req.GenerateFeedbackReq;
import com.speakfit.backend.domain.feedback.dto.res.GenerateFeedbackRes;
import com.speakfit.backend.domain.feedback.dto.res.GetFeedbackDetailRes;
import com.speakfit.backend.domain.feedback.service.FeedbackService;
import com.speakfit.backend.global.apiPayload.response.ApiResponse;
import com.speakfit.backend.global.apiPayload.response.code.SuccessCode;
import com.speakfit.backend.global.config.security.AuthPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/feedbacks")
public class FeedbackController {

    private final FeedbackService feedbackService;

    // 피드백 생성 요청
    @PostMapping
    public ApiResponse<GenerateFeedbackRes> generateFeedback(
            @RequestBody @Valid GenerateFeedbackReq.Request request,
            @AuthenticationPrincipal AuthPrincipal authPrincipal) {
        return ApiResponse.onSuccess(SuccessCode.CREATED,
                feedbackService.generateFeedback(request, authPrincipal.getUserId()));
    }

    // 피드백 상세 조회
    @GetMapping("/{feedbackId}")
    public ApiResponse<GetFeedbackDetailRes> getFeedbackDetail(
            @PathVariable Long feedbackId,
            @AuthenticationPrincipal AuthPrincipal authPrincipal) {
        return ApiResponse.onSuccess(SuccessCode.OK,
                feedbackService.getFeedbackDetail(feedbackId, authPrincipal.getUserId()));
    }

}
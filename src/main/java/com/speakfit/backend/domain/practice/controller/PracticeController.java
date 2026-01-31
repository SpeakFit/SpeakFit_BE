package com.speakfit.backend.domain.practice.controller;

import com.speakfit.backend.domain.practice.dto.req.AnalyzePracticeReq;
import com.speakfit.backend.domain.practice.dto.req.StartPracticeReq;
import com.speakfit.backend.domain.practice.dto.req.StopPracticeReq;
import com.speakfit.backend.domain.practice.dto.res.AnalyzePracticeRes;
import com.speakfit.backend.domain.practice.dto.res.GetPracticeReportRes;
import com.speakfit.backend.domain.practice.dto.res.StartPracticeRes;
import com.speakfit.backend.domain.practice.dto.res.StopPracticeRes;
import com.speakfit.backend.domain.practice.service.PracticeService;
import com.speakfit.backend.global.apiPayload.response.ApiResponse;
import com.speakfit.backend.global.apiPayload.response.code.SuccessCode;
import com.speakfit.backend.global.config.security.AuthPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/practices")
public class PracticeController {

    private final PracticeService practiceService;

    // 발표 연습 시작
    @PostMapping
    public ApiResponse<StartPracticeRes> startPractice(
            @RequestBody @Valid StartPracticeReq.Request request,
            @AuthenticationPrincipal AuthPrincipal authPrincipal) {
        return ApiResponse.onSuccess(SuccessCode.CREATED, practiceService.startPractice(request, authPrincipal.getUserId()));
    }

    // 발표 연습 종료
    @PostMapping(value = "/{practiceId}/stop", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<StopPracticeRes> stopPractice(
            @PathVariable Long practiceId,
            @ModelAttribute @Valid StopPracticeReq.StopPracticeRequest request,
            @AuthenticationPrincipal AuthPrincipal authPrincipal
    ) {
        return ApiResponse.onSuccess(SuccessCode.OK,
                practiceService.stopPractice(practiceId, request, authPrincipal.getUserId()));
    }

    // 발표 분석 요청
    @PostMapping("/analyze")
    public ApiResponse<AnalyzePracticeRes> analyzePractice(
            @RequestBody @Valid AnalyzePracticeReq.Request request,
            @AuthenticationPrincipal AuthPrincipal authPrincipal
    ) {
        return ApiResponse.onSuccess(SuccessCode.OK,
                practiceService.analyzePractice(request, authPrincipal.getUserId()));
    }

    // 발표 분석 결과 조회
    @GetMapping("/{practiceId}/report")
    public ApiResponse<GetPracticeReportRes> getPracticeReport(
            @PathVariable Long practiceId,
            @AuthenticationPrincipal AuthPrincipal authPrincipal
    ) {
        return ApiResponse.onSuccess(SuccessCode.OK,
                practiceService.getPracticeReport(practiceId, authPrincipal.getUserId()));
    }
}
package com.speakfit.backend.domain.practice.controller;

import com.speakfit.backend.domain.practice.dto.req.InputPracticeInfoReq;
import com.speakfit.backend.domain.practice.dto.req.SelectStyleReq;
import com.speakfit.backend.domain.practice.dto.req.StopPracticeReq;
import com.speakfit.backend.domain.practice.dto.res.*;
import com.speakfit.backend.domain.practice.service.PracticeService;
import com.speakfit.backend.global.apiPayload.response.ApiResponse;
import com.speakfit.backend.global.apiPayload.response.code.SuccessCode;
import com.speakfit.backend.global.config.security.AuthPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class PracticeController {

    private final PracticeService practiceService;

    // 발표 연습 정보값 입력 및 스타일 추천 구현
    @PostMapping("/scripts/{scriptId}")
    public ApiResponse<InputPracticeInfoRes.Response> inputPracticeInfo(@PathVariable @Positive Long scriptId,
                                                                        @RequestBody @Valid InputPracticeInfoReq.Request request,
                                                                        @AuthenticationPrincipal AuthPrincipal authPrincipal) {
        return ApiResponse.onSuccess(SuccessCode.CREATED, practiceService.inputPracticeInfo(scriptId, request, authPrincipal.getUserId()));
    }

    // 추천 또는 선택한 발표 스타일 확정 구현
    @PostMapping("/practices/{practiceId}/select-style")
    public ApiResponse<SelectStyleRes.Response> selectStyle(@PathVariable @Positive Long practiceId,
                                           @RequestBody @Valid SelectStyleReq.Request request,
                                           @AuthenticationPrincipal AuthPrincipal authPrincipal) {
        return ApiResponse.onSuccess(SuccessCode.OK, practiceService.selectStyle(practiceId, request, authPrincipal.getUserId()));
    }

    // 발표 연습 시작 (상태 변경 및 정보 반환) 구현
    @PostMapping("/practices/{practiceId}")
    public ApiResponse<StartPracticeRes.Response> startPractice(@PathVariable @Positive Long practiceId,
                                                       @AuthenticationPrincipal AuthPrincipal authPrincipal) {
        return ApiResponse.onSuccess(SuccessCode.OK, practiceService.startPractice(practiceId, authPrincipal.getUserId()));
    }

    // 발표 연습 종료 및 분석 요청 통합 구현
    @PostMapping(
            value = "/practices/{practiceId}/stop",
            consumes = "multipart/form-data"
    )
    public ApiResponse<StopPracticeRes.Response> stopPractice(@PathVariable @Positive Long practiceId,
                                                     @ModelAttribute @Valid StopPracticeReq.Request request,
                                                     @AuthenticationPrincipal AuthPrincipal authPrincipal) {
        return ApiResponse.onSuccess(SuccessCode.OK, practiceService.stopPractice(practiceId, request, authPrincipal.getUserId()));
    }

    // 발표 연습 결과 조회 구현
    @GetMapping("/practices/{practiceId}/report")
    public ApiResponse<GetPracticeReportRes.Response> getPracticeReport(@PathVariable @Positive Long practiceId,
                                                               @AuthenticationPrincipal AuthPrincipal authPrincipal) {
        return ApiResponse.onSuccess(SuccessCode.OK, practiceService.getPracticeReport(practiceId, authPrincipal.getUserId()));
    }
}

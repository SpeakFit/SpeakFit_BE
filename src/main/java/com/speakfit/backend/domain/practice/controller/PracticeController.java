package com.speakfit.backend.domain.practice.controller;

import com.speakfit.backend.domain.practice.dto.req.StartPracticeReq;
import com.speakfit.backend.domain.practice.dto.req.StopPracticeReq;
import com.speakfit.backend.domain.practice.dto.res.StartPracticeRes;
import com.speakfit.backend.domain.practice.dto.res.StopPracticeRes;
import com.speakfit.backend.domain.practice.service.PracticeService;
import com.speakfit.backend.global.apiPayload.response.ApiResponse;
import com.speakfit.backend.global.apiPayload.response.code.SuccessCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/practices")
public class PracticeController {

    private final PracticeService practiceService;

    // 발표 연습 시작
    @PostMapping
    public ApiResponse<StartPracticeRes> startPractice(@RequestBody @Valid StartPracticeReq.Request request) {
        return ApiResponse.onSuccess(SuccessCode.CREATED, practiceService.startPractice(request));
    }

    // 발표 연습 종료
    @PostMapping(value = "/{practiceId}/stop", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<StopPracticeRes> stopPractice(
            @PathVariable Long practiceId,
            @ModelAttribute @Valid StopPracticeReq.Request request
    ) {
        return ApiResponse.onSuccess(SuccessCode.OK, practiceService.stopPractice(practiceId, request));
    }
}
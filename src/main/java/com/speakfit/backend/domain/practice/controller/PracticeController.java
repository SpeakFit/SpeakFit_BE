package com.speakfit.backend.domain.practice.controller;

import com.speakfit.backend.domain.practice.dto.req.StartPracticeReq;
import com.speakfit.backend.domain.practice.dto.res.StartPracticeRes;
import com.speakfit.backend.domain.practice.service.PracticeService;
import com.speakfit.backend.global.apiPayload.response.ApiResponse;
import com.speakfit.backend.global.apiPayload.response.code.SuccessCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/practices")
public class PracticeController {

    private final PracticeService practiceService;

    @PostMapping
    public ApiResponse<StartPracticeRes> startPractice(@RequestBody @Valid StartPracticeReq.Request request) {
        return ApiResponse.onSuccess(SuccessCode.CREATED, practiceService.startPractice(request));
    }
}
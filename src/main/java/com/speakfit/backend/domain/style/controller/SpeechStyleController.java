package com.speakfit.backend.domain.style.controller;

import com.speakfit.backend.domain.style.dto.res.SpeechStylesGetRes;
import com.speakfit.backend.domain.style.service.SpeechStyleQueryService;
import com.speakfit.backend.global.apiPayload.response.ApiResponse;
import com.speakfit.backend.global.apiPayload.response.code.SuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/styles")
public class SpeechStyleController {

    private final SpeechStyleQueryService speechStyleQueryService;

    @GetMapping
    public ApiResponse<SpeechStylesGetRes> getStyles(){
        SpeechStylesGetRes result = speechStyleQueryService.getStyles();

        return ApiResponse.onSuccess(SuccessCode.OK, result);
    }
}

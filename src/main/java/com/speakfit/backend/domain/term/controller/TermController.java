package com.speakfit.backend.domain.term.controller;

import com.speakfit.backend.domain.term.dto.res.TermsGetRes;
import com.speakfit.backend.domain.term.service.TermQueryService;
import com.speakfit.backend.global.apiPayload.response.ApiResponse;
import com.speakfit.backend.global.apiPayload.response.code.SuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/terms")
public class TermController {

    private final TermQueryService termQueryService;

    @GetMapping
    public ApiResponse<TermsGetRes> getTerms(){
        TermsGetRes result = termQueryService.getTerms();
        return ApiResponse.onSuccess(SuccessCode.OK, result);
    }
}

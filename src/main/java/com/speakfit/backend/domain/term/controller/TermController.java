package com.speakfit.backend.domain.term.controller;

import com.speakfit.backend.domain.term.dto.res.TermDetailGetRes;
import com.speakfit.backend.domain.term.dto.res.TermsGetRes;
import com.speakfit.backend.domain.term.service.TermQueryService;
import com.speakfit.backend.global.apiPayload.response.ApiResponse;
import com.speakfit.backend.global.apiPayload.response.code.SuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/terms")
public class TermController {

    private final TermQueryService termQueryService;

    // 약관 목록 조회
    @GetMapping
    public ApiResponse<TermsGetRes> getTerms(){
        TermsGetRes result = termQueryService.getTerms();
        return ApiResponse.onSuccess(SuccessCode.OK, result);
    }

    // 약관 상세 조회
    @GetMapping("/{termId}")
    public ApiResponse<TermDetailGetRes> getTermDetail(@PathVariable Long termId){
        TermDetailGetRes result = termQueryService.getTermDetail(termId);
        return ApiResponse.onSuccess(SuccessCode.OK, result);
    }
}

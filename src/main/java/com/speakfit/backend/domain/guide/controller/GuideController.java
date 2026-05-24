package com.speakfit.backend.domain.guide.controller;

import com.speakfit.backend.domain.guide.dto.req.CreateGuideReq;
import com.speakfit.backend.domain.guide.dto.req.RecommendStyleReq;
import com.speakfit.backend.domain.guide.dto.res.CreateGuideRes;
import com.speakfit.backend.domain.guide.dto.res.RecommendStyleRes;
import com.speakfit.backend.domain.guide.service.GuideService;
import com.speakfit.backend.global.apiPayload.response.ApiResponse;
import com.speakfit.backend.global.apiPayload.response.code.SuccessCode;
import com.speakfit.backend.global.config.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/guides")
public class GuideController {

    private final GuideService guideService;

    // 1번. 청중 맞춤 스피치 스타일 매칭 및 추천 리스트 API
    @PostMapping("/recommendations")
    public ApiResponse<RecommendStyleRes> recommendStyles(
            @RequestBody RecommendStyleReq request,
            @AuthenticationPrincipal AuthPrincipal authPrincipal // 토큰 인증 객체를 주입받아 유저를 식별합니다
    ) {
        // 서비스단에 request와 함께 추출한 토큰 유저 ID를 같이 바인딩합니다.
        return ApiResponse.onSuccess(SuccessCode.OK, guideService.recommendSpeechStyles(request, authPrincipal.getUserId()));
    }

    // 2번. 최종 5대 지표 가이드라인 생성 및 DB 저장 API
    @PostMapping
    public ApiResponse<CreateGuideRes> createGuide(
            @RequestBody CreateGuideReq request,
            @AuthenticationPrincipal AuthPrincipal authPrincipal) {
        return ApiResponse.onSuccess(SuccessCode.CREATED, guideService.createSpeechGuide(request, authPrincipal.getUserId()));
    }
}
package com.speakfit.backend.domain.script.controller;


import com.speakfit.backend.domain.script.dto.req.AddScriptReq;
import com.speakfit.backend.domain.script.dto.req.AiGenerateScriptReq;
import com.speakfit.backend.domain.script.dto.res.AddScriptRes;
import com.speakfit.backend.domain.script.dto.res.AiGenerateScriptRes;
import com.speakfit.backend.domain.script.dto.res.DeleteScriptRes;
import com.speakfit.backend.domain.script.dto.res.GetScriptDetailRes;
import com.speakfit.backend.domain.script.dto.res.GetScriptListRes;
import com.speakfit.backend.domain.script.service.ScriptService;
import com.speakfit.backend.global.apiPayload.response.ApiResponse;
import com.speakfit.backend.global.apiPayload.response.code.SuccessCode;
import com.speakfit.backend.global.config.security.AuthPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/scripts")
public class ScriptController {

    private final ScriptService scriptService;

    // 발표 대본 추가 구현
    @PostMapping
    public ApiResponse<AddScriptRes.Response> addScript(@RequestBody @Valid AddScriptReq.Request request, @AuthenticationPrincipal AuthPrincipal authPrincipal) {
        // 성공 코드 : CREATED(201)
        return ApiResponse.onSuccess(SuccessCode.CREATED, scriptService.addScript(request, authPrincipal.getUserId()));
    }

    // 발표 대본 목록 조회 구현
    @GetMapping
    public ApiResponse<List<GetScriptListRes.Response>> getScripts(@AuthenticationPrincipal AuthPrincipal authPrincipal) {
        return ApiResponse.onSuccess(SuccessCode.OK, scriptService.getScripts(authPrincipal.getUserId()));
    }

    // 발표 대본 상세 조회 구현
    @GetMapping("/{scriptId}")
    public ApiResponse<GetScriptDetailRes.Response> getScript(@PathVariable @Positive Long scriptId, @AuthenticationPrincipal AuthPrincipal authPrincipal) {
        return ApiResponse.onSuccess(SuccessCode.OK, scriptService.getScript(scriptId, authPrincipal.getUserId()));
    }

    // 발표 대본 삭제 구현
    @DeleteMapping("/{scriptId}")
    public ApiResponse<DeleteScriptRes.Response> deleteScript(@PathVariable @Positive Long scriptId, @AuthenticationPrincipal AuthPrincipal authPrincipal) {
        return ApiResponse.onSuccess(SuccessCode.OK, scriptService.deleteScript(scriptId, authPrincipal.getUserId()));
    }

    // AI 발표 대본 초안 생성 구현
    @PostMapping("/ai-generate")
    public ApiResponse<AiGenerateScriptRes.Response> generateScript(@RequestBody @Valid AiGenerateScriptReq.Request request, @AuthenticationPrincipal AuthPrincipal authPrincipal) {
        return ApiResponse.onSuccess(SuccessCode.OK, scriptService.generateScript(request, authPrincipal.getUserId()));
    }
}

package com.speakfit.backend.domain.script.controller;


import com.speakfit.backend.domain.script.dto.req.AddScriptReq;
import com.speakfit.backend.domain.script.dto.res.AddScriptRes;
import com.speakfit.backend.domain.script.dto.res.GetScriptDetailRes;
import com.speakfit.backend.domain.script.dto.res.GetScriptListRes;
import com.speakfit.backend.domain.script.service.ScriptService;
import com.speakfit.backend.global.apiPayload.response.ApiResponse;
import com.speakfit.backend.global.apiPayload.response.code.SuccessCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/scripts")
public class ScriptController {

    private final ScriptService scriptService;

    // 발표 대본 추가
    @PostMapping
    public ApiResponse<AddScriptRes> addScript(@RequestBody @Valid AddScriptReq.Request request) {
        // 성공 코드 : CREATED(201)
        return ApiResponse.onSuccess(SuccessCode.CREATED, scriptService.addScript(request));
    }

    // 발표 대본 목록 조회
    @GetMapping
    public ApiResponse<List<GetScriptListRes>> getScripts(){
        return ApiResponse.onSuccess(SuccessCode.OK,scriptService.getScripts());
    }

    // 발표 대본 상세 조회
    @GetMapping("/{scriptId}")
    public ApiResponse<GetScriptDetailRes> getScript(@PathVariable Long scriptId) {
        return ApiResponse.onSuccess(SuccessCode.OK,scriptService.getScript(scriptId));
    }
}

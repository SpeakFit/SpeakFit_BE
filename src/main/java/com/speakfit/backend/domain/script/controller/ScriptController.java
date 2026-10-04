package com.speakfit.backend.domain.script.controller;


import com.speakfit.backend.domain.script.dto.req.AddScriptReq;
import com.speakfit.backend.domain.script.dto.req.AiGenerateScriptReq;
import com.speakfit.backend.domain.script.dto.req.AiUpdateScriptReq;
import com.speakfit.backend.domain.script.dto.req.UpdateScriptReq;
import com.speakfit.backend.domain.script.dto.res.AddScriptRes;
import com.speakfit.backend.domain.script.dto.res.AiGenerateScriptRes;
import com.speakfit.backend.domain.script.dto.res.AiUpdateScriptRes;
import reactor.core.publisher.Flux;
import com.speakfit.backend.domain.script.dto.res.UpdateScriptRes;
import com.speakfit.backend.domain.script.dto.res.DeleteScriptRes;
import com.speakfit.backend.domain.script.dto.res.GetScriptDetailRes;
import com.speakfit.backend.domain.script.dto.res.GetScriptListRes;
import com.speakfit.backend.domain.script.dto.res.UploadPptRes;
import com.speakfit.backend.domain.script.service.ScriptService;
import com.speakfit.backend.global.apiPayload.response.ApiResponse;
import com.speakfit.backend.global.apiPayload.response.code.SuccessCode;
import com.speakfit.backend.global.config.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/scripts")
public class ScriptController {

    private final ScriptService scriptService;
    private final com.speakfit.backend.domain.practice.service.PracticeService practiceService;

    // [이동] 발표 연습 정보값 입력 및 스타일 추천 구현
    // 기존 PracticeController에서 이동됨. 동일 클래스 내 위치시켜 ai-update와의 경로 충돌 해결.
    @PostMapping("/{scriptId:[0-9]+}")
    public com.speakfit.backend.global.apiPayload.response.ApiResponse<com.speakfit.backend.domain.practice.dto.res.InputPracticeInfoRes.Response> inputPracticeInfo(
            @PathVariable @Positive Long scriptId,
            @RequestBody @Valid com.speakfit.backend.domain.practice.dto.req.InputPracticeInfoReq.Request request,
            @AuthenticationPrincipal AuthPrincipal authPrincipal) {
        return com.speakfit.backend.global.apiPayload.response.ApiResponse.onSuccess(
                com.speakfit.backend.global.apiPayload.response.code.SuccessCode.CREATED,
                practiceService.inputPracticeInfo(scriptId, request, authPrincipal.getUserId()));
    }

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
    @GetMapping("/{scriptId:[0-9]+}")
    public ApiResponse<GetScriptDetailRes.Response> getScript(@PathVariable @Positive Long scriptId, @AuthenticationPrincipal AuthPrincipal authPrincipal) {
        return ApiResponse.onSuccess(SuccessCode.OK, scriptService.getScript(scriptId, authPrincipal.getUserId()));
    }

    // 발표 대본 삭제 구현
    @DeleteMapping("/{scriptId:[0-9]+}")
    public ApiResponse<DeleteScriptRes.Response> deleteScript(@PathVariable @Positive Long scriptId, @AuthenticationPrincipal AuthPrincipal authPrincipal) {
        return ApiResponse.onSuccess(SuccessCode.OK, scriptService.deleteScript(scriptId, authPrincipal.getUserId()));
    }

    // 발표 대본 수정 구현
    @PatchMapping("/{scriptId:[0-9]+}")
    public ApiResponse<UpdateScriptRes.Response> updateScript(@PathVariable @Positive Long scriptId, @RequestBody @Valid UpdateScriptReq.Request request, @AuthenticationPrincipal AuthPrincipal authPrincipal) {
        return ApiResponse.onSuccess(SuccessCode.OK, scriptService.updateScript(scriptId, request, authPrincipal.getUserId()));
    }

    // [STEP-A] AI 발표 대본 초안 생성 — SSE 스트리밍 반환
    // Spring이 Flux<String>을 TEXT_EVENT_STREAM으로 자동 인코딩 → data: {...}\n\n
    // FE 수신 이벤트: data: {"chunk": "..."} | data: [DONE] | data: {"error": "..."}
    @PostMapping("/ai-generate")
    public Flux<String> generateScript(
            @RequestBody @Valid AiGenerateScriptReq.Request request,
            @AuthenticationPrincipal AuthPrincipal authPrincipal) {
        return scriptService.generateScript(request, authPrincipal.getUserId());
    }

    // [STEP-A] AI 발표 대본 최적화 — SSE 스트리밍 반환
    @PostMapping("/ai-update")
    public Flux<String> updateScript(
            @RequestBody @Valid AiUpdateScriptReq.Request request,
            @AuthenticationPrincipal AuthPrincipal authPrincipal) {
        return scriptService.updateScript(request, authPrincipal.getUserId());
    }

    // PPT 변환 상태 및 슬라이드 조회 구현
    @GetMapping("/{scriptId:[0-9]+}/ppt/status")
    public ApiResponse<UploadPptRes.Response> getPptStatus(@PathVariable @Positive Long scriptId,
                                                           @AuthenticationPrincipal AuthPrincipal authPrincipal) {
        return ApiResponse.onSuccess(SuccessCode.OK, scriptService.getPptStatus(scriptId, authPrincipal.getUserId()));
    }

    // PPT 파일 업로드 및 슬라이드 변환 구현
    @PatchMapping(value = "/{scriptId:[0-9]+}/ppt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<UploadPptRes.Response>> uploadPpt(@PathVariable @Positive Long scriptId,
                                                                         @Parameter(content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
                                                                                 schema = @Schema(type = "string", format = "binary")))
                                                                         @RequestPart("file") MultipartFile file,
                                                                         @AuthenticationPrincipal AuthPrincipal authPrincipal) {
        return ResponseEntity
                .status(SuccessCode.ACCEPTED.getHttpStatus())
                .body(ApiResponse.onSuccess(SuccessCode.ACCEPTED, scriptService.uploadPpt(scriptId, file, authPrincipal.getUserId())));
    }
}

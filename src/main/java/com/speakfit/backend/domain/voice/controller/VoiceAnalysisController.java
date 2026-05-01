package com.speakfit.backend.domain.voice.controller;

import com.speakfit.backend.domain.voice.DTO.res.VoiceAnalysisResultRes;
import com.speakfit.backend.domain.voice.service.VoiceAnalysisService;
import com.speakfit.backend.global.apiPayload.response.ApiResponse;
import com.speakfit.backend.global.apiPayload.response.code.SuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/voice-analysis")
@RequiredArgsConstructor
public class VoiceAnalysisController {

    private final VoiceAnalysisService voiceAnalysisService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<VoiceAnalysisResultRes>> requestVoiceAnalysis(
            @RequestParam(value = "voiceFile") MultipartFile voiceFile) {

        VoiceAnalysisResultRes result = voiceAnalysisService.requestVoiceAnalysis(voiceFile);
        return ResponseEntity.ok(ApiResponse.onSuccess(SuccessCode.OK, result));
    }
}
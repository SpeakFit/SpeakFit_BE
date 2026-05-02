package com.speakfit.backend.domain.voice.service;

import com.speakfit.backend.domain.voice.dto.res.VoiceAnalysisResultRes;
import org.springframework.web.multipart.MultipartFile;

public interface VoiceAnalysisService {
    VoiceAnalysisResultRes requestVoiceAnalysis(MultipartFile voiceFile);
    VoiceAnalysisResultRes getVoiceAnalysisResult(Long analysisId);
}
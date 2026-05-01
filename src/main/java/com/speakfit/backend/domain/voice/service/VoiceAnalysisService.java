package com.speakfit.backend.domain.voice.service;

import com.speakfit.backend.domain.voice.DTO.res.VoiceAnalysisResultRes;
import org.springframework.web.multipart.MultipartFile;

public interface VoiceAnalysisService {
    VoiceAnalysisResultRes requestVoiceAnalysis(MultipartFile voiceFile);
}
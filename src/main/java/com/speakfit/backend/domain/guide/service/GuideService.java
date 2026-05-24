package com.speakfit.backend.domain.guide.service;

import com.speakfit.backend.domain.guide.dto.req.CreateGuideReq;
import com.speakfit.backend.domain.guide.dto.req.RecommendStyleReq;
import com.speakfit.backend.domain.guide.dto.res.CreateGuideRes;
import com.speakfit.backend.domain.guide.dto.res.RecommendStyleRes;

public interface GuideService {
    RecommendStyleRes recommendSpeechStyles(RecommendStyleReq req, Long userId);
    CreateGuideRes createSpeechGuide(CreateGuideReq req, Long userId);
}
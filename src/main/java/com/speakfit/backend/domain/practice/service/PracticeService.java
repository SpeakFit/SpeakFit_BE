package com.speakfit.backend.domain.practice.service;

import com.speakfit.backend.domain.practice.dto.req.StartPracticeReq;
import com.speakfit.backend.domain.practice.dto.res.StartPracticeRes;

public interface PracticeService {
    StartPracticeRes startPractice(StartPracticeReq.Request request);
}

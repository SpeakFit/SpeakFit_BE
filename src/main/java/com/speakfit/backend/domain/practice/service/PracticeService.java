package com.speakfit.backend.domain.practice.service;

import com.speakfit.backend.domain.practice.dto.req.StartPracticeReq;
import com.speakfit.backend.domain.practice.dto.req.StopPracticeReq;
import com.speakfit.backend.domain.practice.dto.res.StartPracticeRes;
import com.speakfit.backend.domain.practice.dto.res.StopPracticeRes;

public interface PracticeService {
    // 발표 연습 시작 메소드 정의
    StartPracticeRes startPractice(StartPracticeReq.Request request);

    // 발표 연습 종료 메소드 정의
    StopPracticeRes stopPractice(Long practiceId, StopPracticeReq.Request request);
}


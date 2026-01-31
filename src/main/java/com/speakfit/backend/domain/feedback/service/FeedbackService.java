package com.speakfit.backend.domain.feedback.service;

import com.speakfit.backend.domain.feedback.dto.req.GenerateFeedbackReq;
import com.speakfit.backend.domain.feedback.dto.res.GenerateFeedbackRes;

public interface FeedbackService {

    // 피드백 생성 요청 메소드 정의
    GenerateFeedbackRes generateFeedback(GenerateFeedbackReq.Request request,Long userId);

}

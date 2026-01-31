package com.speakfit.backend.domain.feedback.service;

import com.speakfit.backend.domain.feedback.dto.req.GenerateFeedbackReq;
import com.speakfit.backend.domain.feedback.dto.res.GenerateFeedbackRes;
import com.speakfit.backend.domain.feedback.dto.res.GetFeedbackDetailRes;

public interface FeedbackService {

    // 피드백 생성 요청 메소드 정의
    GenerateFeedbackRes generateFeedback(GenerateFeedbackReq.Request request,Long userId);

    // 피드백 상세 조회 요청 메소드 정의
    GetFeedbackDetailRes getFeedbackDetail(Long feedbackId, Long userId);
}

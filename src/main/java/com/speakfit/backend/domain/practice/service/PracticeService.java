package com.speakfit.backend.domain.practice.service;

import com.speakfit.backend.domain.practice.dto.req.InputPracticeInfoReq;
import com.speakfit.backend.domain.practice.dto.req.SelectStyleReq;
import com.speakfit.backend.domain.practice.dto.req.StopPracticeReq;
import com.speakfit.backend.domain.practice.dto.res.*;

public interface PracticeService {
    // 발표 연습 정보값 입력 및 스타일 추천 서비스 정의
    InputPracticeInfoRes.Response inputPracticeInfo(Long scriptId, InputPracticeInfoReq.Request req, Long userId);

    // 추천 또는 선택한 발표 스타일 확정 및 낭독 기호 생성 서비스 정의
    SelectStyleRes.Response selectStyle(Long practiceId, SelectStyleReq.Request req, Long userId);

    // 발표 연습 시작 (실제 녹음/분석 활성화) 서비스 정의
    StartPracticeRes.Response startPractice(Long practiceId, Long userId);

    // 발표 연습 종료 및 분석 트리거 서비스 정의
    StopPracticeRes.Response stopPractice(Long practiceId, StopPracticeReq.Request req, Long userId);

    // 발표 연습 결과 리포트 조회 서비스 정의
    GetPracticeReportRes.Response getPracticeReport(Long practiceId, Long userId);
}

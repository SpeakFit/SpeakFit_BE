package com.speakfit.backend.domain.practice.service;

import com.speakfit.backend.domain.practice.dto.req.InputPracticeInfoReq;
import com.speakfit.backend.domain.practice.dto.req.SelectStyleReq;
import com.speakfit.backend.domain.practice.dto.req.StopPracticeReq;
import com.speakfit.backend.domain.practice.dto.res.*;

public interface PracticeService {
    // 발표 연습 정보값 입력 및 스타일 추천
    InputPracticeInfoRes.Response inputPracticeInfo(Long scriptId, InputPracticeInfoReq.Request request, Long userId);

    // 추천 또는 선택한 발표 스타일 확정 및 낭독 기호 생성
    SelectStyleRes selectStyle(Long practiceId, SelectStyleReq.Request request, Long userId);

    // 발표 연습 시작 (상태 변경 및 정보 반환)
    StartPracticeRes startPractice(Long practiceId, Long userId);

    // 발표 연습 종료 및 분석 트리거
    StopPracticeRes stopPractice(Long practiceId, StopPracticeReq.Request request, Long userId);

    // 발표 분석 결과 조회
    GetPracticeReportRes getPracticeReport(Long practiceId, Long userId);
}

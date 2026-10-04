package com.speakfit.backend.domain.practice.service;

import com.speakfit.backend.domain.practice.entity.PracticeRecord;
import com.speakfit.backend.domain.script.entity.Script;
import com.speakfit.backend.domain.style.entity.SpeechStyle;

public interface AiAnalysisService {
    // 비동기 분석 및 결과 저장 로직 정의
    void processAnalysisAsync(Long practiceId, String audioUrl);

    // 파이썬 서버에 대본 기호 생성(Marking) 요청 정의 (동기 — 내부 테스트용 유지)
    String generateMarkedContent(String content);

    // [STEP-B] 낭독기호 비동기 생성 — 대본 저장 후 백그라운드에서 Gemini 호출, 완료 시 DB 갱신
    void generateMarkedContentAsync(Long scriptId, String content);
}

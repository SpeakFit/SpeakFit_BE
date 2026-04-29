package com.speakfit.backend.domain.practice.service;

import com.speakfit.backend.domain.practice.dto.res.PythonAnalysisRes;

public interface AiAnalysisTxService {
    // 분석 결과 데이터 저장 로직 정의
    void saveResults(Long practiceId, PythonAnalysisRes data);

    // 분석 실패 시 상태 처리 로직 정의
    void handleAnalysisFailure(Long practiceId);
}

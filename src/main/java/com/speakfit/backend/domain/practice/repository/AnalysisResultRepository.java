package com.speakfit.backend.domain.practice.repository;

import com.speakfit.backend.domain.practice.entity.AnalysisResult;
import com.speakfit.backend.domain.practice.entity.PracticeRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AnalysisResultRepository extends JpaRepository<AnalysisResult, Long> {

    // 존재 여부 확인
    boolean existsByPracticeRecord(PracticeRecord practiceRecord);

    Optional<AnalysisResult> findByPracticeRecord(PracticeRecord practiceRecord);

    // 삭제
    void deleteByPracticeRecord(PracticeRecord practiceRecord);

    // N+1 문제 해결을 위한 PracticeRecord 리스트 일괄 조회 메서드
    // @param practiceRecords 연습 기록 리스트
    // @return 분석 결과 리스트
    List<AnalysisResult> findByPracticeRecordIn(List<PracticeRecord> practiceRecords);
}
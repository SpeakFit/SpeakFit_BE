package com.speakfit.backend.domain.practice.repository;

import com.speakfit.backend.domain.practice.entity.AnalysisResult;
import com.speakfit.backend.domain.practice.entity.PracticeRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AnalysisResultRepository extends JpaRepository<AnalysisResult, Long> {
    // 존재 여부 확인
    boolean existsByPracticeRecord(PracticeRecord practiceRecord);

    Optional<AnalysisResult> findByPracticeRecord(PracticeRecord practiceRecord);

    // 삭제
    void deleteByPracticeRecord(PracticeRecord practiceRecord);
}

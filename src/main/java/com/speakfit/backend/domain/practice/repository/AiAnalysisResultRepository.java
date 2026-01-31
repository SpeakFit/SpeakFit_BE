package com.speakfit.backend.domain.practice.repository;

import com.speakfit.backend.domain.practice.entity.AiAnalysisResult;
import com.speakfit.backend.domain.practice.entity.PracticeRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiAnalysisResultRepository extends JpaRepository<AiAnalysisResult, Long> {
    // 존재 여부 확인
    boolean existsByPracticeRecord(PracticeRecord practiceRecord);

    Optional<AiAnalysisResult> findByPracticeRecord(PracticeRecord practiceRecord);

    // 삭제
    void deleteByPracticeRecord(PracticeRecord practiceRecord);
}

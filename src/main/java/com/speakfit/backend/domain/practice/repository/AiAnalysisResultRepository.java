package com.speakfit.backend.domain.practice.repository;

import com.speakfit.backend.domain.practice.entity.AiAnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiAnalysisResultRepository extends JpaRepository<AiAnalysisResult, Long> {
}

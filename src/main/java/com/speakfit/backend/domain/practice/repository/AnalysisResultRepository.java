package com.speakfit.backend.domain.practice.repository;

import com.speakfit.backend.domain.practice.entity.AnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisResultRepository extends JpaRepository<AnalysisResult, Long> {
}

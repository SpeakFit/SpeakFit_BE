package com.speakfit.backend.domain.guide.repository;

import com.speakfit.backend.domain.guide.entity.SpeechGuide;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpeechGuideRepository extends JpaRepository<SpeechGuide, Long> {
}
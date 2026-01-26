package com.speakfit.backend.domain.style.repository;

import com.speakfit.backend.domain.style.entity.SpeechStyle;
import com.speakfit.backend.domain.style.enums.StyleType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SpeechStyleRepository extends JpaRepository<SpeechStyle, Long> {

    Optional<SpeechStyle> findByStyleType(StyleType styleType);
}

package com.speakfit.backend.domain.voice.repository;

import com.speakfit.backend.domain.voice.entity.BaselineVoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BaselineVoiceRepository extends JpaRepository<BaselineVoice, Long> {
    List<BaselineVoice> findAllByUserIdAndIsActiveTrue(Long userId);
    Optional<BaselineVoice> findFirstByUserIdAndIsActiveTrueOrderByIdDesc(Long userId);
}

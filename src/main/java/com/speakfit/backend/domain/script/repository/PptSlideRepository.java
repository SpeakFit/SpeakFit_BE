package com.speakfit.backend.domain.script.repository;

import com.speakfit.backend.domain.script.entity.PptSlide;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PptSlideRepository extends JpaRepository<PptSlide, Long> {
    List<PptSlide> findAllByScriptIdOrderBySlideIndexAsc(Long scriptId);
}

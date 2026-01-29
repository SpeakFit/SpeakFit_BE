package com.speakfit.backend.domain.practice.repository;


import com.speakfit.backend.domain.practice.entity.PracticeRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PracticeRepository extends JpaRepository<PracticeRecord,Long> {
}

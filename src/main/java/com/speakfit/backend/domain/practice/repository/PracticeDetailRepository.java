package com.speakfit.backend.domain.practice.repository;

import com.speakfit.backend.domain.practice.entity.PracticeDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PracticeDetailRepository extends JpaRepository<PracticeDetail, Long> {
    List<PracticeDetail> findAllByPracticeRecordIdOrderByWordIndexAsc(Long recordId);
}

package com.speakfit.backend.domain.practice.repository;

import com.speakfit.backend.domain.practice.entity.PracticeSentenceResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PracticeSentenceResultRepository extends JpaRepository<PracticeSentenceResult, Long> {

    List<PracticeSentenceResult> findAllByPracticeRecordIdOrderBySentenceIndexAsc(Long recordId);

    void deleteAllByPracticeRecordId(Long recordId);
}

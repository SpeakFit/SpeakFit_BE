package com.speakfit.backend.domain.practice.repository;

import com.speakfit.backend.domain.practice.entity.PracticeWordResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PracticeWordResultRepository extends JpaRepository<PracticeWordResult, Long> {

    List<PracticeWordResult> findAllByPracticeRecordIdOrderByGlobalWordIndexAsc(Long recordId);

    void deleteAllByPracticeRecordId(Long recordId);
}

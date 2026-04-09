package com.speakfit.backend.domain.practice.repository;

import com.speakfit.backend.domain.practice.entity.PracticeIssue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PracticeIssueRepository extends JpaRepository<PracticeIssue, Long> {
    List<PracticeIssue> findAllByPracticeRecordId(Long recordId);
}

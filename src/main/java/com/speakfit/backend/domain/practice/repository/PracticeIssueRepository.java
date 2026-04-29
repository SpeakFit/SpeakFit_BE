package com.speakfit.backend.domain.practice.repository;

import com.speakfit.backend.domain.practice.entity.PracticeIssue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PracticeIssueRepository extends JpaRepository<PracticeIssue, Long> {
    List<PracticeIssue> findAllByPracticeRecordId(Long recordId);

    List<PracticeIssue> findAllByPracticeRecordIdOrderByDisplayOrderAscIdAsc(Long recordId);

    @Modifying(flushAutomatically = true)
    @Query("delete from PracticeIssue pi where pi.practiceRecord.id = :recordId")
    void deleteAllByPracticeRecordId(@Param("recordId") Long recordId);
}

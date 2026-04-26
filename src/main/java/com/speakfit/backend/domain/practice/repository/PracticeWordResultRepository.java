package com.speakfit.backend.domain.practice.repository;

import com.speakfit.backend.domain.practice.entity.PracticeWordResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PracticeWordResultRepository extends JpaRepository<PracticeWordResult, Long> {

    List<PracticeWordResult> findAllByPracticeRecordIdOrderByGlobalWordIndexAsc(Long recordId);

    @Modifying(flushAutomatically = true)
    @Query("delete from PracticeWordResult pwr where pwr.practiceRecord.id = :recordId")
    void deleteAllByPracticeRecordId(@Param("recordId") Long recordId);
}

package com.speakfit.backend.domain.practice.repository;

import com.speakfit.backend.domain.practice.entity.PracticeSentenceResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PracticeSentenceResultRepository extends JpaRepository<PracticeSentenceResult, Long> {

    @Query("select psr from PracticeSentenceResult psr left join fetch psr.scriptSentence where psr.practiceRecord.id = :recordId order by psr.sentenceIndex asc")
    List<PracticeSentenceResult> findAllByPracticeRecordIdOrderBySentenceIndexAsc(@Param("recordId") Long recordId);

    void deleteAllByPracticeRecordId(Long recordId);
}

package com.speakfit.backend.domain.practice.repository;


import com.speakfit.backend.domain.practice.entity.PracticeRecord;
import com.speakfit.backend.domain.user.entity.User;
import com.speakfit.backend.domain.practice.enums.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PracticeRepository extends JpaRepository<PracticeRecord, Long> {

    // 사용자, 상태, 생성일자 범위로 연습기록조회
    List<PracticeRecord> findAllByUserAndStatusAndCreatedAtBetween(
            User user,
            Status status,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime
    );

    // FETCH JOIN을 사용하여 연관된 script와 speechStyle을 미리 로드합니다.
    @Query("SELECT p FROM PracticeRecord p " +
            "JOIN FETCH p.script " +
            "left JOIN FETCH p.speechStyle " +
            "WHERE p.id = :id")
    Optional<PracticeRecord> findByIdWithDetails(@Param("id") Long id);

}

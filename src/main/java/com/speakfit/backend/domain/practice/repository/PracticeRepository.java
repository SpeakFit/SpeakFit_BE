package com.speakfit.backend.domain.practice.repository;


import com.speakfit.backend.domain.practice.entity.PracticeRecord;
import com.speakfit.backend.domain.user.entity.User;
import com.speakfit.backend.domain.practice.enums.PracticeStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface PracticeRepository extends JpaRepository<PracticeRecord, Long> {

    // 사용자, 상태, 생성일자 범위로 연습기록조회
    List<PracticeRecord> findAllByUserAndStatusAndCreatedAtBetween(
            User user,
            PracticeStatus status,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime
    );

}

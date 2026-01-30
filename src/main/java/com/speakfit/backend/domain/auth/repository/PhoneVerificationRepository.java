package com.speakfit.backend.domain.auth.repository;

import com.speakfit.backend.domain.auth.entity.PhoneVerification;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PhoneVerificationRepository extends JpaRepository<PhoneVerification, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PhoneVerification> findByVerificationId(String verificationId);

    long countByPhoneNumAndCreatedAtAfter(String phoneNum, LocalDateTime after);
}

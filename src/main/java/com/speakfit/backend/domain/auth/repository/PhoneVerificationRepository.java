package com.speakfit.backend.domain.auth.repository;

import com.speakfit.backend.domain.auth.entity.PhoneVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface PhoneVerificationRepository extends JpaRepository<PhoneVerification, Long> {

    Optional<PhoneVerification> findByVerificationId(String verificationId);

    long countByPhoneNumAndCreatedAtAfter(String phoneNum, Instant after);
}

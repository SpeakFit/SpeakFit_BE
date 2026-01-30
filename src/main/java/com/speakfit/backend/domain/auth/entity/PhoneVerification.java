package com.speakfit.backend.domain.auth.entity;

import com.speakfit.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "phone_verification",
        indexes = {
                @Index(name = "idx_phone_verification_phone", columnList = "phoneNum"),
                @Index(name = "idx_phone_verification_expires", columnList = "expiresAt")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PhoneVerification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String verificationId;

    @Column(nullable = false, length = 20)
    private String phoneNum;

    @Column(nullable = false, length = 128)
    private String codeHash;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean verified;

    @Column(nullable = false)
    private int attemptCount;

    public static PhoneVerification create(String verificationId,
                                           String phoneNum,
                                           String codeHash,
                                           Instant expiresAt) {
        PhoneVerification pv = new PhoneVerification();
        pv.verificationId = verificationId;
        pv.phoneNum = phoneNum;
        pv.codeHash = codeHash;
        pv.expiresAt = expiresAt;
        pv.verified = false;
        pv.attemptCount = 0;
        return pv;
    }
}

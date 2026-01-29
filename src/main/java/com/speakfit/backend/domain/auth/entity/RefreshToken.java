package com.speakfit.backend.domain.auth.entity;

import com.speakfit.backend.domain.user.entity.User;
import com.speakfit.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Table(name = "refresh_tokens",
        uniqueConstraints = @UniqueConstraint(name = "uk_refresh_token_user", columnNames = "user_id")
)
public class RefreshToken extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 2000)
    private String token;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public void updateToken(String newToken, Instant newExpiresAt) {
        this.token = newToken;
        this.expiresAt = newExpiresAt;
    }
    public boolean isExpired(){
        return expiresAt.isBefore(Instant.now());
    }
}

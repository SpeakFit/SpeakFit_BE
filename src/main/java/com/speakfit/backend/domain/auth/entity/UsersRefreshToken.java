package com.speakfit.backend.domain.auth.entity;

import com.speakfit.backend.domain.users.entity.Users;
import com.speakfit.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Table(name = "user_refresh_token",
        uniqueConstraints = @UniqueConstraint(name = "uk_rt_user", columnNames = "user_id")
)
public class UsersRefreshToken extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @Column(nullable = false, length = 512)
    private String token;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public void rotate(String newToken, Instant newExpiresAt) {
        this.token = newToken;
        this.expiresAt = newExpiresAt;
    }
}

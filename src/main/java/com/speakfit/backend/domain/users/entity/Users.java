package com.speakfit.backend.domain.users.entity;

import com.speakfit.backend.domain.style.entity.SpeechStyle;
import com.speakfit.backend.domain.users.enums.Dialect;
import com.speakfit.backend.domain.users.enums.Gender;
import com.speakfit.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDate;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Table(name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_users_users_id", columnNames = "users_id"),
                @UniqueConstraint(name = "uk_users_phone", columnNames = "phone_num"),
                @UniqueConstraint(name = "uk_users_nickname", columnNames = "nickname")
        }
)
public class Users extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "style_id", nullable = false)
    private SpeechStyle style;

    @Column(name = "users_id", nullable = false, length = 50)
    private String usersId;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(nullable = false, length = 10)
    private String name;

    @Column(name = "phone_num", nullable = false, length = 11)
    private String phoneNum;

    @Column(nullable = false, length = 20)
    private String nickname;

    @Column(nullable = false)
    private LocalDate birth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Gender gender;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @ColumnDefault("'SEOUL'")
    @Builder.Default
    private Dialect dialect = Dialect.SEOUL;
}

package com.speakfit.backend.domain.user.entity;

import com.speakfit.backend.domain.user.enums.Dialect;
import com.speakfit.backend.domain.user.enums.Gender;
import com.speakfit.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

// 사용자 정보를 나타내는 엔티티 클래스입니다.
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Table(name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_email", columnNames = "email"),
                @UniqueConstraint(name = "uk_user_nickname", columnNames = "nickname")
        }
)
public class User extends BaseEntity {

    // 사용자 고유 식별자(ID)
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 사용자 이메일
    @Column(nullable = false, length = 255)
    private String email;

    // 사용자 비밀번호
    @Column(nullable = false, length = 255)
    private String password;

    // 사용자 닉네임
    @Column(nullable = false, length = 20)
    private String nickname;

    // 사용자 생년월일
    @Column(nullable = false, length = 255)
    private String birthday;

    // 사용자 성별
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Gender gender;

    // 사용자가 사용하는 사투리(Dialect) 타입
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @ColumnDefault("'STANDARD'")
    @Builder.Default
    private Dialect dialect = Dialect.STANDARD;

    // 사용자의 기본 음색 정보를 포함하는 임베디드(Embedded) 필드입니다.
    @Embedded
    private DefaultVoice defaultVoice;

    // 사용자의 기본 음색 지표를 설정(최초 저장)하거나 수정합니다.
    public void updateDefaultVoiceMetrics(Double defaultPitch, Double defaultWpm) {
        if (this.defaultVoice == null) {
            this.defaultVoice = DefaultVoice.builder()
                    .defaultPitch(defaultPitch)
                    .defaultWpm(defaultWpm)
                    .build();
        } else {
            this.defaultVoice.updateMetrics(defaultPitch, defaultWpm);
        }
    }
}
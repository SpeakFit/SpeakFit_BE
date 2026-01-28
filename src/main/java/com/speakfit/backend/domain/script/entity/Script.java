package com.speakfit.backend.domain.script.entity;

import com.speakfit.backend.domain.user.entity.User;
import com.speakfit.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "script",indexes = {
        @Index(name = "idx_script_user_id", columnList = "user_id")}) // ERD의 테이블명과 일치
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Script extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) // 대본은 여러 개 -> 유저는 한개
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100) // 제목 길이 제한 (임의설정, 필요시 조정)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

}

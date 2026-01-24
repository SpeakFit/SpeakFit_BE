package com.speakfit.backend.domain.term.entity.mapping;

import com.speakfit.backend.domain.term.entity.Term;
import com.speakfit.backend.domain.users.entity.User;
import com.speakfit.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Table(
        name = "users_terms",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_users_terms", columnNames = {"user_id", "term_id"})
        }
)
public class UserTerm extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "term_id", nullable = false)
    private Term term;

    @Column(nullable = false)
    private boolean agreed;
}

package com.speakfit.backend.domain.terms.entity;

import com.speakfit.backend.domain.terms.enums.TermsType;
import com.speakfit.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Table(
        name = "terms",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_terms_type", columnNames = "terms_type")
        }
)
public class Terms extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "terms_type", nullable = false, length = 30)
    private TermsType termsType;

    @Column(nullable = false, length = 100)
    private String title;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private boolean required;
}

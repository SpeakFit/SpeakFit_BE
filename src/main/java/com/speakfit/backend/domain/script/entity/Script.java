package com.speakfit.backend.domain.script.entity;

import com.speakfit.backend.domain.script.enums.ScriptType;
import com.speakfit.backend.domain.user.entity.User;
import com.speakfit.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "script", indexes = {
        @Index(name = "idx_script_user_id", columnList = "user_id")})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Script extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "script_type")
    private ScriptType scriptType;

    @Column(name = "marked_content", columnDefinition = "TEXT")
    private String markedContent;

    @Column(name = "ppt_url", columnDefinition = "TEXT")
    private String pptUrl;

    @Column(name = "total_slides")
    private Integer totalSlides;

    @Builder.Default
    @OneToMany(mappedBy = "script", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PptSlide> pptSlides = new ArrayList<>();

    // 연관관계 편의 메서드
    public void addPptSlide(PptSlide pptSlide) {
        this.pptSlides.add(pptSlide);
        if (pptSlide.getScript() != this) {
            pptSlide.setScript(this);
        }
    }
}

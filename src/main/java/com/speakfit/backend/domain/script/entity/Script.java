package com.speakfit.backend.domain.script.entity;

import com.speakfit.backend.domain.script.enums.PptStatus;
import com.speakfit.backend.domain.script.enums.ScriptType;
import com.speakfit.backend.domain.user.entity.User;
import com.speakfit.backend.global.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
    @Enumerated(EnumType.STRING)
    @Column(name = "ppt_status")
    private PptStatus pptStatus = PptStatus.NONE;

    @Column(name = "ppt_error_message", columnDefinition = "TEXT")
    private String pptErrorMessage;

    @Builder.Default
    @OneToMany(mappedBy = "script", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PptSlide> pptSlides = new ArrayList<>();

    public void addPptSlide(PptSlide pptSlide) {
        this.pptSlides.add(pptSlide);
        if (pptSlide.getScript() != this) {
            pptSlide.setScript(this);
        }
    }

    public void updateMarkedContent(String markedContent) {
        this.markedContent = markedContent;
    }

    public void updatePptInfo(String pptUrl, Integer totalSlides) {
        this.scriptType = ScriptType.PPT;
        this.pptUrl = pptUrl;
        this.totalSlides = totalSlides;
        this.pptStatus = PptStatus.COMPLETED;
        this.pptErrorMessage = null;
        this.pptSlides.clear();
    }

    public void markPptProcessing() {
        this.pptStatus = PptStatus.PROCESSING;
        this.pptErrorMessage = null;
    }

    public void markPptFailed(String errorMessage) {
        this.pptStatus = PptStatus.FAILED;
        this.pptErrorMessage = errorMessage;
    }

    @PrePersist
    @PostLoad
    private void normalizePptStatus() {
        if (this.pptStatus == null) {
            this.pptStatus = this.pptUrl != null ? PptStatus.COMPLETED : PptStatus.NONE;
        }
    }
}

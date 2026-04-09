package com.speakfit.backend.domain.practice.dto.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class StartPracticeRes {
    private Long practiceId;
    private String title; // 스크립트 제목
    private String webSocketUrl;
    private String status; // RECORDING
    private List<ContentRes> contentList;
    private LocalDateTime createdAt;

    @Getter
    @Builder
    @AllArgsConstructor
    public static class ContentRes {
        private Integer index;
        private String word;
        private Boolean hasBreak; // ' / ' 기호 여부
        private Boolean isEmphasis; // ' * ' 기호 여부
    }
}

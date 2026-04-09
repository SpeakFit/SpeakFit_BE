package com.speakfit.backend.domain.practice.dto.res;

import com.speakfit.backend.domain.practice.enums.Status;
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
    private String title;
    private String webSocketUrl;
    private Status status;
    private List<ContentRes> contentList;
    private LocalDateTime createdAt;

    @Getter
    @Builder
    @AllArgsConstructor
    public static class ContentRes {
        private Integer index;
        private String word;
        private Boolean hasBreak;
        private Boolean isEmphasis;
    }
}

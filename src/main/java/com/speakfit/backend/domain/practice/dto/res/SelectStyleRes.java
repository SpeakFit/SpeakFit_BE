package com.speakfit.backend.domain.practice.dto.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class SelectStyleRes {
    private Long practiceId;
    private List<ContentRes> contentList;

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

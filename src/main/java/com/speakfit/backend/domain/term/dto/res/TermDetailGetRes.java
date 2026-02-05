package com.speakfit.backend.domain.term.dto.res;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TermDetailGetRes {
    private Long termId;
    private String title;
    private String content;
}

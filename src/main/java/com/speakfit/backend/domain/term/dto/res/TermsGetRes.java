package com.speakfit.backend.domain.term.dto.res;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class TermsGetRes {

    private List<TermItem> terms;

    @Getter
    @Builder
    public static class TermItem{
        private Long termId;
        private String title;
        private boolean required;
    }
}

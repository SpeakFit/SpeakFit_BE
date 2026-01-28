package com.speakfit.backend.domain.script.dto.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class AddScriptRes {
    private Long id;
    private String title;
    private String content;
    private LocalDateTime createdAt;
}

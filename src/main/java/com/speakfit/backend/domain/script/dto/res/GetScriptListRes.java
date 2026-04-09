package com.speakfit.backend.domain.script.dto.res;

import com.speakfit.backend.domain.script.enums.ScriptType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class GetScriptListRes {
    private Long id;
    private String title;
    private String content; // String (Text)
    private ScriptType scriptType; // 추가: PPT인지 TEXT인지 구분
    private LocalDateTime createdAt;
}

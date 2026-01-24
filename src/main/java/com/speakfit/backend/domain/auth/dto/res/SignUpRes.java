package com.speakfit.backend.domain.auth.dto.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class SignUpRes {
    private Long userId;
    private String usersId;
    private String nickname;
}

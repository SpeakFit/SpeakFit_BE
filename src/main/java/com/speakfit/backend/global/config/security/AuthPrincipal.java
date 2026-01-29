package com.speakfit.backend.global.config.security;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class AuthPrincipal {
    private final Long userId;      // 멤버 아이디
    private final String usersId;   // 로그인 아이디
}

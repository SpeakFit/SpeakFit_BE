package com.speakfit.backend.domain.auth.service;

import com.speakfit.backend.domain.auth.dto.req.LoginReq;
import com.speakfit.backend.domain.auth.dto.req.SignUpReq;
import com.speakfit.backend.domain.auth.dto.res.LoginRes;
import com.speakfit.backend.domain.auth.dto.res.SignUpRes;

public interface AuthService {

    SignUpRes signUp(SignUpReq.Request request);

    LoginRes login(LoginReq.Request request);

    /** 쿠키의 리프레시 토큰으로 access/refresh 토큰을 재발급한다. (refresh 토큰 회전) */
    LoginRes refresh(String refreshToken);

    /** 리프레시 토큰을 폐기한다. 이미 만료/무효여도 예외 없이 끝난다. (멱등) */
    void logout(String refreshToken);
}

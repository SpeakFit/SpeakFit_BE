package com.speakfit.backend.domain.auth.service;

import com.speakfit.backend.domain.auth.dto.req.LoginReq;
import com.speakfit.backend.domain.auth.dto.req.SignUpReq;
import com.speakfit.backend.domain.auth.dto.res.LoginRes;
import com.speakfit.backend.domain.auth.dto.res.SignUpRes;

public interface AuthService {

    SignUpRes signUp(SignUpReq.Request request);

    LoginRes login(LoginReq.Request request);
}

package com.speakfit.backend.domain.auth.service;

import com.speakfit.backend.domain.auth.dto.req.SignUpReq;
import com.speakfit.backend.domain.auth.dto.res.SignUpRes;

public interface AuthService {

    SignUpRes signUp(SignUpReq.Request request);
}

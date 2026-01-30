package com.speakfit.backend.domain.auth.service;

import com.speakfit.backend.domain.auth.dto.req.PhoneSendReq;
import com.speakfit.backend.domain.auth.dto.req.PhoneVerifyReq;
import com.speakfit.backend.domain.auth.dto.res.PhoneSendRes;
import com.speakfit.backend.domain.auth.dto.res.PhoneVerifyRes;

public interface PhoneVerificationService {
    // 전화번호 인증 sms 코드 전송
    PhoneSendRes sendCode(PhoneSendReq req);

    // 전화번호 인증 sms 코드 확인
    PhoneVerifyRes verifyCode(PhoneVerifyReq req);
}

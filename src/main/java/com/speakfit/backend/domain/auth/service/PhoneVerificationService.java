package com.speakfit.backend.domain.auth.service;

import com.speakfit.backend.domain.auth.dto.req.PhoneSendReq;
import com.speakfit.backend.domain.auth.dto.res.PhoneSendRes;

public interface PhoneVerificationService {
    PhoneSendRes sendCode(PhoneSendReq req);
}

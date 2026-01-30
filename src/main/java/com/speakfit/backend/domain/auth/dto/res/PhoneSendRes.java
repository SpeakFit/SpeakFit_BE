package com.speakfit.backend.domain.auth.dto.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class PhoneSendRes {

    private String verificationId;
    private int expiresInSec;
}

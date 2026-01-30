package com.speakfit.backend.global.infra.sms;

public interface SmsClient {
    void send(String toPhoneNum, String message);
}

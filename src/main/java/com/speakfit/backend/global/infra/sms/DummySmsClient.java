package com.speakfit.backend.global.infra.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile({"local", "test"})
public class DummySmsClient implements SmsClient{
    @Override
    public void send(String toPhoneNum, String message){
        // 로컬에서는 실제 전송 대신 로그로
        log.info(("[DUMMY SMS] to={} message={}", toPhoneNum, message));
    }
}

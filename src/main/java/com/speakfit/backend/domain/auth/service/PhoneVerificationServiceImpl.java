package com.speakfit.backend.domain.auth.service;

import com.speakfit.backend.domain.auth.dto.req.PhoneSendReq;
import com.speakfit.backend.domain.auth.dto.res.PhoneSendRes;
import com.speakfit.backend.domain.auth.entity.PhoneVerification;
import com.speakfit.backend.domain.auth.exception.AuthErrorCode;
import com.speakfit.backend.domain.auth.repository.PhoneVerificationRepository;
import com.speakfit.backend.global.apiPayload.exception.CustomException;
import com.speakfit.backend.global.infra.sms.SmsClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Transactional
public class PhoneVerificationServiceImpl implements PhoneVerificationService{

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final int EXPIRES_SEC = 180;
    private static final int CODE_LEN = 6;

    private final PhoneVerificationRepository phoneVerificationRepository;
    private final SmsClient smsClient;

    @Override
    public PhoneSendRes sendCode(PhoneSendReq req){
        String phone = req.getPhoneNum();

        // 레이트리밋: 1분 내 3회 이상이면 차단
        LocalDateTime oneMinuteAgo = LocalDateTime.now().minusMinutes(1);
        long recentCount = phoneVerificationRepository.countByPhoneNumAndCreatedAtAfter(phone, oneMinuteAgo);
        if(recentCount >= 3){
            throw new CustomException(AuthErrorCode.PHONE_TOO_MANY_REQUESTS);
        }

        String code = generateNumericCode(CODE_LEN);
        String codeHash = sha256(code);

        String verificationId = "vfy_" + randomBase36(6);
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(EXPIRES_SEC);

        PhoneVerification pv = PhoneVerification.create(verificationId, phone, codeHash, expiresAt);
        phoneVerificationRepository.save(pv);

        // 실제 문자 발송
        try {
            smsClient.send(phone, "[SpeakFit] 인증번호는 " + code + " 입니다. (" + EXPIRES_SEC + "초 내 입력)");
        } catch (Exception e) {
            // 벤더 실패 시 502 추천
            throw new CustomException(AuthErrorCode.SMS_SEND_FAILED);
        }

        return PhoneSendRes.builder()
                .verificationId(verificationId)
                .expiresInSec(EXPIRES_SEC)
                .build();
    }

    private String generateNumericCode(int len) {
        int bound = (int) Math.pow(10, len);
        int n = SECURE_RANDOM.nextInt(bound);
        return String.format("%0" + len + "d", n);
    }

    private String randomBase36(int len) {
        final String chars = "0123456789abcdefghijklmnopqrstuvwxyz";
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(chars.charAt(SECURE_RANDOM.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new CustomException(AuthErrorCode.INTERNAL_HASH_ERROR);
        }
    }
}

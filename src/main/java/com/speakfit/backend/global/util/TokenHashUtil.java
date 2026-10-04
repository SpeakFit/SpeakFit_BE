package com.speakfit.backend.global.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 리프레시 토큰을 DB에 평문으로 저장하지 않기 위한 SHA-256 해시 유틸. */
public final class TokenHashUtil {

    private TokenHashUtil() {}

    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 을 사용할 수 없습니다.", e);
        }
    }

    /** 타이밍 공격을 피하기 위한 상수 시간 비교. */
    public static boolean matches(String expectedHash, String actualHash) {
        return MessageDigest.isEqual(
                expectedHash.getBytes(StandardCharsets.UTF_8),
                actualHash.getBytes(StandardCharsets.UTF_8));
    }
}

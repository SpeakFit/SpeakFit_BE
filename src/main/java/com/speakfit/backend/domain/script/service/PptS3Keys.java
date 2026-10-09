package com.speakfit.backend.domain.script.service;

import java.util.UUID;
import java.util.regex.Pattern;

/** PPT 관련 S3 오브젝트 키 규칙. ppt/{scriptId}/attempts/{uuid}/ 아래에 원본과 슬라이드를 둔다. */
final class PptS3Keys {

    private static final Pattern ATTEMPT_PREFIX = Pattern.compile("^ppt/\\d+/attempts/[^/]+$");

    private PptS3Keys() {
    }

    static String scriptPrefix(Long scriptId) {
        return "ppt/" + scriptId;
    }

    static String newAttemptPrefix(Long scriptId) {
        return scriptPrefix(scriptId) + "/attempts/" + UUID.randomUUID();
    }

    /**
     * 이전 변환 시도의 prefix 를 돌려준다. 객체 키가 ppt/{id}/attempts/{uuid}/... 형태가 아니면 null
     * (엉뚱한 prefix 를 통째로 지우지 않기 위한 방어).
     */
    static String attemptPrefixOf(String objectKey) {
        if (objectKey == null) {
            return null;
        }
        int lastSlash = objectKey.lastIndexOf('/');
        if (lastSlash < 0) {
            return null;
        }
        String parent = objectKey.substring(0, lastSlash);
        return ATTEMPT_PREFIX.matcher(parent).matches() ? parent : null;
    }
}

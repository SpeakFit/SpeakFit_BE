package com.speakfit.backend.domain.script.service;

public interface PptConvertAsyncService {

    /**
     * @param sourcePptUrl  S3 에 올려 둔 원본 파일 URL
     * @param attemptPrefix 이번 변환 시도의 S3 prefix (원본과 변환 결과가 이 아래에 저장된다)
     */
    void convertPptAsync(Long scriptId, Long userId, String sourcePptUrl, String attemptPrefix, String previousPptUrl);
}

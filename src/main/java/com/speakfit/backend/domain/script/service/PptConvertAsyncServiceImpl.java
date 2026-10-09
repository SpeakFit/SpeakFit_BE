package com.speakfit.backend.domain.script.service;

import com.speakfit.backend.domain.script.dto.res.PptConvertRes;
import com.speakfit.backend.domain.script.dto.res.UploadPptRes;
import com.speakfit.backend.domain.script.exception.ScriptErrorCode;
import com.speakfit.backend.global.apiPayload.exception.CustomException;
import com.speakfit.backend.global.infra.s3.S3Service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PPT 변환 비동기 처리.
 * 스프링과 분석 서버가 서로 다른 서버일 수 있으므로 파일은 S3 로만 주고받는다.
 * (원본은 호출 전에 S3 에 올라가 있고, 분석 서버가 내려받아 변환한 슬라이드를 S3 에 올린다.)
 */
@Slf4j
@Service
public class PptConvertAsyncServiceImpl implements PptConvertAsyncService {

    private final ScriptTxService scriptTxService;
    private final WebClient webClient;
    private final S3Service s3Service;

    public PptConvertAsyncServiceImpl(
            ScriptTxService scriptTxService,
            @Qualifier("webClient") WebClient webClient,
            S3Service s3Service
    ) {
        this.scriptTxService = scriptTxService;
        this.webClient = webClient;
        this.s3Service = s3Service;
    }

    @Override
    @Async("pptConvertExecutor")
    public void convertPptAsync(Long scriptId, Long userId, String sourcePptUrl, String attemptPrefix, String previousPptUrl) {
        try {
            PptConvertRes.Response convertResponse = requestPptConvert(sourcePptUrl, attemptPrefix);
            List<UploadPptRes.PptSlideRes> slides = convertResponse.getSlides().stream()
                    .map(slide -> UploadPptRes.PptSlideRes.builder()
                            .page(slide.getPage())
                            .imageUrl(slide.getImageUrl())
                            .build())
                    .toList();

            String savedSourceUrl = convertResponse.getSourcePptUrl() != null
                    ? convertResponse.getSourcePptUrl()
                    : sourcePptUrl;
            scriptTxService.savePptSuccess(scriptId, userId, savedSourceUrl, convertResponse.getTotalSlides(), slides);
            deletePreviousAttemptQuietly(previousPptUrl, attemptPrefix);
        } catch (Exception e) {
            log.error("PPT 변환 비동기 처리 실패 - scriptId: {}", scriptId, e);
            deletePrefixQuietly(attemptPrefix);
            try {
                scriptTxService.markPptFailed(scriptId, userId, "PPT slide conversion failed.");
            } catch (Exception updateException) {
                log.warn("PPT 변환 실패 상태 저장 실패 - scriptId: {}", scriptId, updateException);
            }
        }
    }

    private PptConvertRes.Response requestPptConvert(String sourcePptUrl, String attemptPrefix) {
        Map<String, Object> body = new HashMap<>();
        body.put("pptUrl", sourcePptUrl);
        body.put("outputPrefix", attemptPrefix);

        try {
            PptConvertRes.Response response = webClient.post()
                    .uri("/ppt/convert")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(PptConvertRes.Response.class)
                    .block();

            if (response == null || response.getSlides() == null || response.getSlides().isEmpty()) {
                throw new CustomException(ScriptErrorCode.SCRIPT_PPT_CONVERT_FAILED);
            }

            return response;
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("PPT slide conversion request failed - sourcePptUrl: {}", sourcePptUrl, e);
            throw new CustomException(ScriptErrorCode.SCRIPT_PPT_CONVERT_FAILED);
        }
    }

    /** 새 변환이 성공하면 이전 변환 시도(원본과 슬라이드)를 S3 에서 지운다. */
    private void deletePreviousAttemptQuietly(String previousPptUrl, String currentAttemptPrefix) {
        String previousPrefix = PptS3Keys.attemptPrefixOf(s3Service.extractObjectKey(previousPptUrl));
        if (previousPrefix == null || previousPrefix.equals(currentAttemptPrefix)) {
            return;
        }

        deletePrefixQuietly(previousPrefix);
    }

    private void deletePrefixQuietly(String prefix) {
        try {
            s3Service.deleteByPrefix(prefix);
        } catch (Exception e) {
            log.warn("PPT S3 파일 정리 실패 - prefix: {}", prefix, e);
        }
    }
}

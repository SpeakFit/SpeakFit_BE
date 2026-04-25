package com.speakfit.backend.domain.script.service;

import com.speakfit.backend.domain.script.dto.res.PptConvertRes;
import com.speakfit.backend.domain.script.dto.res.UploadPptRes;
import com.speakfit.backend.domain.script.exception.ScriptErrorCode;
import com.speakfit.backend.global.apiPayload.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PptConvertAsyncServiceImpl implements PptConvertAsyncService {

    private static final Path UPLOAD_ROOT_PATH = Paths.get("uploads").toAbsolutePath().normalize();

    private final ScriptTxService scriptTxService;
    private final WebClient webClient;

    @Override
    @Async("pptConvertExecutor")
    public void convertPptAsync(Long scriptId, Long userId, String sourcePptPath, Path uploadDirPath, String previousPptUrl) {
        try {
            PptConvertRes.Response convertResponse = requestPptConvert(sourcePptPath, getPptOutputDir(uploadDirPath));
            List<UploadPptRes.PptSlideRes> slides = convertResponse.getSlides().stream()
                    .map(slide -> UploadPptRes.PptSlideRes.builder()
                            .page(slide.getPage())
                            .imageUrl(toUploadUrl(Paths.get(slide.getImageUrl())))
                            .build())
                    .toList();

            scriptTxService.savePptSuccess(scriptId, userId, toUploadUrl(Paths.get(sourcePptPath)), convertResponse.getTotalSlides(), slides);
            deletePreviousPptAttemptQuietly(previousPptUrl, uploadDirPath);
        } catch (Exception e) {
            log.error("PPT 변환 비동기 처리 실패 - scriptId: {}", scriptId, e);
            deleteDirectoryQuietly(uploadDirPath);
            try {
                scriptTxService.markPptFailed(scriptId, userId, "PPT slide conversion failed.");
            } catch (Exception updateException) {
                log.warn("PPT 변환 실패 상태 저장 실패 - scriptId: {}", scriptId, updateException);
            }
        }
    }

    private PptConvertRes.Response requestPptConvert(String sourcePptUrl, String outputDir) {
        Map<String, Object> body = new HashMap<>();
        body.put("pptPath", sourcePptUrl);
        body.put("outputDir", outputDir);

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

    private String getPptOutputDir(Path uploadDirPath) {
        return uploadDirPath.resolve("converted").normalize().toString();
    }

    private String toUploadUrl(Path filePath) {
        Path absoluteFilePath = filePath.toAbsolutePath().normalize();
        if (!absoluteFilePath.startsWith(UPLOAD_ROOT_PATH)) {
            return absoluteFilePath.toString().replace("\\", "/");
        }

        String relativePath = UPLOAD_ROOT_PATH.relativize(absoluteFilePath).toString().replace("\\", "/");
        return "/uploads/" + relativePath;
    }

    private Path toStoragePath(String uploadUrl) {
        if (uploadUrl == null || uploadUrl.isBlank()) {
            return null;
        }

        String normalizedUrl = uploadUrl.replace("\\", "/");
        if (normalizedUrl.startsWith("/uploads/")) {
            return UPLOAD_ROOT_PATH.resolve(normalizedUrl.substring("/uploads/".length())).normalize();
        }

        if (normalizedUrl.startsWith("uploads/")) {
            return UPLOAD_ROOT_PATH.resolve(normalizedUrl.substring("uploads/".length())).normalize();
        }

        return Paths.get(uploadUrl).toAbsolutePath().normalize();
    }

    private void deleteDirectoryQuietly(Path directoryPath) {
        if (directoryPath == null || !Files.exists(directoryPath)) {
            return;
        }

        try {
            try (var paths = Files.walk(directoryPath)) {
                paths.sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (Exception e) {
                                log.warn("File cleanup failed - path: {}", path, e);
                            }
                        });
            }
        } catch (Exception e) {
            log.warn("PPT upload directory cleanup failed - path: {}", directoryPath, e);
        }
    }

    private void deletePreviousPptAttemptQuietly(String previousPptUrl, Path currentUploadDirPath) {
        if (previousPptUrl == null || previousPptUrl.isBlank()) {
            return;
        }

        Path previousFilePath = toStoragePath(previousPptUrl);
        if (previousFilePath == null) {
            return;
        }

        Path previousUploadDirPath = previousFilePath.getParent();
        if (previousUploadDirPath == null || previousUploadDirPath.getParent() == null) {
            return;
        }

        Path attemptsDirPath = previousUploadDirPath.getParent();
        if (!"attempts".equals(attemptsDirPath.getFileName().toString())) {
            return;
        }

        if (previousUploadDirPath.equals(currentUploadDirPath)) {
            return;
        }

        deleteDirectoryQuietly(previousUploadDirPath);
    }
}

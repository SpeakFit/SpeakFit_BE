package com.speakfit.backend.domain.voice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.speakfit.backend.domain.user.entity.User;
import com.speakfit.backend.domain.user.repository.UserRepository;
import com.speakfit.backend.domain.voice.dto.res.PythonVoiceAnalysisRes;
import com.speakfit.backend.domain.voice.dto.res.VoiceAnalysisResultRes;
import com.speakfit.backend.domain.voice.entity.BaselineVoice;
import com.speakfit.backend.domain.voice.enums.BaselineVoiceStatus;
import com.speakfit.backend.domain.voice.exception.VoiceException;
import com.speakfit.backend.domain.voice.exception.VoiceExceptionStatus;
import com.speakfit.backend.domain.voice.repository.BaselineVoiceRepository;
import com.speakfit.backend.global.infra.s3.S3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

@Service
@RequiredArgsConstructor
@Slf4j
public class VoiceAnalysisServiceImpl implements VoiceAnalysisService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final S3Service s3Service;
    private final WebClient pythonWebClient;
    private final UserRepository userRepository;
    private final BaselineVoiceRepository baselineVoiceRepository;

    @Value("${app.ai.response-timeout-seconds:900}")
    private long analysisTimeoutSeconds;

    @Value("${app.voice.min-file-size-bytes:5120}")
    private long minVoiceFileSizeBytes;

    @Override
    @Transactional(noRollbackFor = VoiceException.class)
    public VoiceAnalysisResultRes requestVoiceAnalysis(MultipartFile voiceFile, Long userId) {
        // 1. 온보딩 기준 음성으로 쓸 수 있는 파일인지 먼저 검증
        if (voiceFile == null || voiceFile.isEmpty()) {
            log.warn("Voice analysis upload rejected: userId={}, reason=empty_file", userId);
            throw new VoiceException(VoiceExceptionStatus.VOICE_DATA_INSUFFICIENT);
        }

        log.info(
                "Voice analysis upload received: userId={}, size={} bytes, contentType={}, originalFilename={}, minSize={} bytes",
                userId,
                voiceFile.getSize(),
                voiceFile.getContentType(),
                voiceFile.getOriginalFilename(),
                minVoiceFileSizeBytes
        );

        if (isDataInsufficient(voiceFile)) {
            log.warn(
                    "Voice analysis upload rejected: userId={}, reason=file_too_small, size={} bytes, minSize={} bytes, contentType={}, originalFilename={}",
                    userId,
                    voiceFile.getSize(),
                    minVoiceFileSizeBytes,
                    voiceFile.getContentType(),
                    voiceFile.getOriginalFilename()
            );
            throw new VoiceException(VoiceExceptionStatus.VOICE_DATA_INSUFFICIENT);
        }

        Path tempPath = null;
        BaselineVoice baselineVoice = null;
        try {
            // 2. 로그인 사용자를 기준으로 기준 음성 도메인 데이터를 준비
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new VoiceException(VoiceExceptionStatus.VOICE_USER_NOT_FOUND));

            baselineVoice = baselineVoiceRepository.save(BaselineVoice.builder()
                    .user(user)
                    .build());

            String audioUrl = s3Service.upload(voiceFile, "voice/baseline/" + userId);
            baselineVoice.updateAudioUrl(audioUrl);
            baselineVoiceRepository.save(baselineVoice);

            // 3. Python 분석 서버에 전달할 임시 파일 생성
            tempPath = Files.createTempFile("voice", getFileExtension(voiceFile.getOriginalFilename()));
            File tempFile = tempPath.toFile();
            voiceFile.transferTo(tempFile);

            // [STEP 2-B] gender를 Python /voice-analysis에 전달 (pitch 성별 필터용)
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("voiceFile", new FileSystemResource(tempFile));
            if (user.getGender() != null) {
                body.add("gender", user.getGender().name());
            }

            PythonVoiceAnalysisRes response = pythonWebClient.post()
                    .uri("/voice-analysis")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(PythonVoiceAnalysisRes.class)
                    .timeout(Duration.ofSeconds(analysisTimeoutSeconds))
                    .block();

            // 4. Python 분석 실패 시 BaselineVoice를 실패 상태로 남기고 예외 반환
            if (response == null || !"COMPLETED".equals(response.getStatus())) {
                failBaselineVoice(baselineVoice, response == null ? null : OBJECT_MAPPER.writeValueAsString(response));
                throw new VoiceException(VoiceExceptionStatus.VOICE_ANALYSIS_FAILED);
            }

            // 5. 분석 결과를 BaselineVoice에 저장하고 현재 active 기준 음성으로 전환
            baselineVoiceRepository.findAllByUserIdAndIsActiveTrue(userId)
                    .forEach(BaselineVoice::deactivate);
            baselineVoice.complete(
                    response.getAvgPitch(),
                    response.getAvgWpm(),
                    response.getAvgIntensity(),
                    response.getAvgZcr(),
                    response.getPauseRatio(),
                    OBJECT_MAPPER.writeValueAsString(response)
            );

            // 6. 추천 로직에서 빠르게 읽을 수 있도록 User.defaultVoice 스냅샷 동기화
            user.updateDefaultVoiceMetrics(response.getAvgPitch(), response.getAvgWpm());
            userRepository.save(user);

            return toResponse(baselineVoice);

        } catch (WebClientResponseException e) {
            log.warn(
                    "Voice analysis Python API returned error: userId={}, status={}, body={}",
                    userId,
                    e.getStatusCode().value(),
                    e.getResponseBodyAsString()
            );
            failBaselineVoice(baselineVoice, e.getResponseBodyAsString());
            if (e.getStatusCode().value() == 422) {
                throw new VoiceException(VoiceExceptionStatus.VOICE_UNPROCESSABLE);
            }
            if (e.getStatusCode().value() == 408 || e.getStatusCode().value() == 504) {
                throw new VoiceException(VoiceExceptionStatus.VOICE_ANALYSIS_TIMEOUT);
            }
            throw new VoiceException(VoiceExceptionStatus.VOICE_ANALYSIS_FAILED);
        } catch (WebClientRequestException e) {
            log.warn("Voice analysis Python API request failed: userId={}, message={}", userId, e.getMessage(), e);
            failBaselineVoice(baselineVoice, null);
            if (isTimeoutException(e)) {
                throw new VoiceException(VoiceExceptionStatus.VOICE_ANALYSIS_TIMEOUT);
            }
            throw new VoiceException(VoiceExceptionStatus.VOICE_DATA_INSUFFICIENT);
        } catch (RuntimeException e) {
            if (e instanceof VoiceException) {
                throw e;
            }
            log.warn("Voice analysis failed at runtime: userId={}, message={}", userId, e.getMessage(), e);
            failBaselineVoice(baselineVoice, null);
            if (isTimeoutException(e)) {
                throw new VoiceException(VoiceExceptionStatus.VOICE_ANALYSIS_TIMEOUT);
            }
            throw e;
        } catch (IOException e) {
            log.warn("Voice analysis failed while handling file: userId={}, message={}", userId, e.getMessage(), e);
            failBaselineVoice(baselineVoice, null);
            throw new VoiceException(VoiceExceptionStatus.VOICE_UNPROCESSABLE);
        } finally {
            if (tempPath != null) {
                try {
                    Files.deleteIfExists(tempPath);
                } catch (IOException e) {
                    // 예외 무시
                }
            }
        }
    }

    private boolean isTimeoutException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof TimeoutException || current.getClass().getSimpleName().contains("Timeout")) {
                return true;
            }
            current = current.getCause();
        }

        return false;
    }

    private void failBaselineVoice(BaselineVoice baselineVoice, String analysisRawJson) {
        if (baselineVoice != null) {
            baselineVoice.fail(analysisRawJson);
        }
    }

    private boolean isDataInsufficient(MultipartFile file) {
        return file.getSize() < minVoiceFileSizeBytes;
    }

    @Override
    @Transactional(readOnly = true)
    public VoiceAnalysisResultRes getVoiceAnalysisResult(Long analysisId) {
        // 저장된 기준 음성 분석 결과를 BaselineVoice 기준으로 조회
        BaselineVoice baselineVoice = baselineVoiceRepository.findById(analysisId)
                .orElseThrow(() -> new VoiceException(VoiceExceptionStatus.VOICE_ANALYSIS_NOT_FOUND));

        return toResponse(baselineVoice);
    }

    private VoiceAnalysisResultRes toResponse(BaselineVoice baselineVoice) {
        return VoiceAnalysisResultRes.builder()
                .analysisId(baselineVoice.getId())
                .status(baselineVoice.getStatus().name())
                .progress(baselineVoice.getStatus() == BaselineVoiceStatus.COMPLETED ? 100 : 0)
                .userAverageMetrics(VoiceAnalysisResultRes.UserAverageMetrics.builder()
                        .avgPitch(baselineVoice.getAvgPitch())
                        .avgWPM(baselineVoice.getAvgWpm())
                        .build())
                .build();
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return ".tmp";
        }

        String cleanFileName = Paths.get(fileName).getFileName().toString();
        int dotIndex = cleanFileName.lastIndexOf(".");
        if (dotIndex < 0 || dotIndex == cleanFileName.length() - 1) {
            return ".tmp";
        }

        return cleanFileName.substring(dotIndex);
    }
}

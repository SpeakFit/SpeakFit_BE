package com.speakfit.backend.domain.voice.service;

import com.speakfit.backend.domain.practice.entity.AnalysisResult;
import com.speakfit.backend.domain.practice.entity.PracticeRecord;
import com.speakfit.backend.domain.practice.repository.AnalysisResultRepository;
import com.speakfit.backend.domain.practice.repository.PracticeRepository;
import com.speakfit.backend.domain.user.entity.User;
import com.speakfit.backend.domain.user.repository.UserRepository;
import com.speakfit.backend.domain.voice.dto.res.VoiceAnalysisResultRes;
import com.speakfit.backend.domain.voice.exception.VoiceException;
import com.speakfit.backend.domain.voice.exception.VoiceExceptionStatus;
import com.speakfit.backend.global.infra.s3.S3Service;
import lombok.RequiredArgsConstructor;
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

@Service
@RequiredArgsConstructor
public class VoiceAnalysisServiceImpl implements VoiceAnalysisService {

    private final S3Service s3Service;
    private final WebClient pythonWebClient;
    private final AnalysisResultRepository analysisResultRepository;
    private final PracticeRepository practiceRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public VoiceAnalysisResultRes requestVoiceAnalysis(MultipartFile voiceFile) {
        if (voiceFile == null || voiceFile.isEmpty()) {
            throw new VoiceException(VoiceExceptionStatus.VOICE_DATA_INSUFFICIENT);
        }

        if (isDataInsufficient(voiceFile)) {
            throw new VoiceException(VoiceExceptionStatus.VOICE_DATA_INSUFFICIENT);
        }

        Path tempPath = null;
        try {
            tempPath = Files.createTempFile("voice", ".mp3");
            File tempFile = tempPath.toFile();
            voiceFile.transferTo(tempFile);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("voiceFile", new FileSystemResource(tempFile));

            VoiceAnalysisResultRes response = pythonWebClient.post()
                    .uri("/voice-analysis")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(VoiceAnalysisResultRes.class)
                    .block();

            if (response != null && "COMPLETED".equals(response.getStatus())) {
                Long recordId = response.getAnalysisId();
                PracticeRecord practiceRecord = practiceRepository.findById(recordId)
                        .orElseThrow(() -> new IllegalArgumentException("해당하는 연습 기록이 없습니다."));

                Double avgWpm = null;
                Double avgPitch = null;
                if (response.getUserAverageMetrics() != null) {
                    avgWpm = response.getUserAverageMetrics().getAvgWPM();
                    avgPitch = response.getUserAverageMetrics().getAvgPitch();
                }

                String mostSimilarStyle = null;
                Integer matchingRate = null;
                String voiceStyleDescription = null;

                if (response.getVoiceStyle() != null) {
                    mostSimilarStyle = response.getVoiceStyle().getMostSimilarStyle();
                    matchingRate = response.getVoiceStyle().getMatchingRate();
                    voiceStyleDescription = response.getVoiceStyle().getDescription();
                }

                AnalysisResult analysisResult = AnalysisResult.builder()
                        .recordId(recordId)
                        .practiceRecord(practiceRecord)
                        .avgWpm(avgWpm)
                        .avgPitch(avgPitch)
                        .mostSimilarStyle(mostSimilarStyle)
                        .matchingRate(matchingRate)
                        .voiceStyleDescription(voiceStyleDescription)
                        .build();

                analysisResultRepository.save(analysisResult);

                User user = practiceRecord.getUser();

                if (user.getDefaultVoice() == null ||
                        (user.getDefaultVoice().getDefaultPitch() == null && user.getDefaultVoice().getDefaultWpm() == null)) {

                    user.updateDefaultVoiceMetrics(avgPitch, avgWpm);
                    userRepository.save(user);
                }
            }

            return response;

        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 422) {
                throw new VoiceException(VoiceExceptionStatus.VOICE_UNPROCESSABLE);
            }
            throw new VoiceException(VoiceExceptionStatus.VOICE_DATA_INSUFFICIENT);
        } catch (WebClientRequestException e) {
            throw new VoiceException(VoiceExceptionStatus.VOICE_DATA_INSUFFICIENT);
        } catch (IOException e) {
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

    private boolean isDataInsufficient(MultipartFile file) {
        return file.getSize() < 50 * 1024;
    }

    @Override
    @Transactional(readOnly = true)
    public VoiceAnalysisResultRes getVoiceAnalysisResult(Long analysisId) {
        AnalysisResult analysisResult = analysisResultRepository.findById(analysisId)
                .orElseThrow(() -> new VoiceException(VoiceExceptionStatus.VOICE_ANALYSIS_NOT_FOUND));

        Double wpm = analysisResult.getAvgWpm();
        Double pitch = analysisResult.getAvgPitch();

        return VoiceAnalysisResultRes.builder()
                .analysisId(analysisResult.getRecordId())
                .status("COMPLETED")
                .progress(100)
                .voiceStyle(VoiceAnalysisResultRes.VoiceStyle.builder()
                        .mostSimilarStyle(analysisResult.getMostSimilarStyle())
                        .matchingRate(analysisResult.getMatchingRate())
                        .description(analysisResult.getVoiceStyleDescription())
                        .build())
                .userAverageMetrics(VoiceAnalysisResultRes.UserAverageMetrics.builder()
                        .avgPitch(pitch)
                        .avgWPM(wpm)
                        .build())
                .build();
    }
}
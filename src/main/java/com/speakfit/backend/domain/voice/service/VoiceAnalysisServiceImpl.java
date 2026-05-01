package com.speakfit.backend.domain.voice.service;

import com.speakfit.backend.domain.practice.entity.AnalysisResult;
import com.speakfit.backend.domain.practice.entity.PracticeRecord;
import com.speakfit.backend.domain.practice.repository.AnalysisResultRepository;
import com.speakfit.backend.domain.practice.repository.PracticeRepository;
import com.speakfit.backend.domain.user.entity.User;
import com.speakfit.backend.domain.user.repository.UserRepository;
import com.speakfit.backend.domain.voice.DTO.res.VoiceAnalysisResultRes;
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
    private final UserRepository userRepository; // User 엔티티 저장을 위해 추가

    @Override
    @Transactional
    public VoiceAnalysisResultRes requestVoiceAnalysis(MultipartFile voiceFile) {

        // 1. 음성 데이터 파일 검증
        if (voiceFile == null || voiceFile.isEmpty()) {
            throw new VoiceException(VoiceExceptionStatus.VOICE_DATA_INSUFFICIENT);
        }

        // 2. 데이터 부족 검증 로직
        if (isDataInsufficient(voiceFile)) {
            throw new VoiceException(VoiceExceptionStatus.VOICE_DATA_INSUFFICIENT);
        }

        try {
            // 3. 임시 파일 생성 및 Python 서버 API 호출 준비
            Path tempPath = Files.createTempFile("voice", ".mp3");
            File tempFile = tempPath.toFile();
            voiceFile.transferTo(tempFile);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("voiceFile", new FileSystemResource(tempFile));

            // 4. Python 서버 API 호출
            VoiceAnalysisResultRes response = pythonWebClient.post()
                    .uri("/voice-analysis")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(VoiceAnalysisResultRes.class)
                    .block();

            // 5. 임시 파일 삭제
            Files.deleteIfExists(tempPath);

            // 6. 분석 완료 시 처리 로직
            if (response != null && "COMPLETED".equals(response.getStatus())) {
                Long recordId = response.getAnalysisId();
                PracticeRecord practiceRecord = practiceRepository.findById(recordId)
                        .orElseThrow(() -> new IllegalArgumentException("해당하는 연습 기록이 없습니다."));

                // 분석 결과 엔티티 생성 및 저장
                AnalysisResult analysisResult = AnalysisResult.builder()
                        .recordId(recordId)
                        .practiceRecord(practiceRecord)
                        .avgWpm(response.getAvgWpm())
                        .avgPitch(response.getAvgPitch())
                        .build();

                analysisResultRepository.save(analysisResult);

                // User 엔티티의 최초 기본 음색 저장 로직 (최초 1회만 저장)
                User user = practiceRecord.getUser();
                if (user.getDefaultVoice() == null ||
                        user.getDefaultVoice().getDefaultPitch() == null ||
                        user.getDefaultVoice().getDefaultWpm() == null) {

                    user.updateDefaultVoiceMetrics(response.getAvgPitch(), response.getAvgWpm());
                    userRepository.save(user); // JPA의 변경 감지 또는 save를 통해 DB 반영
                }
            }

            return response;

        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 422) {
                throw new VoiceException(VoiceExceptionStatus.VOICE_UNPROCESSABLE);
            }
            throw new VoiceException(VoiceExceptionStatus.VOICE_DATA_INSUFFICIENT);
        } catch (IOException e) {
            throw new VoiceException(VoiceExceptionStatus.VOICE_UNPROCESSABLE);
        }
    }

    private boolean isDataInsufficient(MultipartFile file) {
        // 파일의 크기가 최소 요구사항에 미치지 못하는지 확인 (예: 50KB 미만)
        return file.getSize() < 50 * 1024;
    }
}
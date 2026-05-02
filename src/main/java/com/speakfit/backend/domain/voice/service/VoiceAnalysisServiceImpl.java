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
    private final UserRepository userRepository; // User 엔티티 저장을 위해 추가

    public VoiceAnalysisResultRes requestVoiceAnalysis(MultipartFile voiceFile) {

        // 1. [임시 확인용]수정된 DTO 구조에 맞게 더미 객체 생성
        return VoiceAnalysisResultRes.builder()
                .analysisId(1L)
                .status("COMPLETED")
                .progress(100)
                .userAverageMetrics(VoiceAnalysisResultRes.UserAverageMetrics.builder()
                        .avgPitch(200.0)
                        .avgWPM(150.0) // DTO의 필드명과 일치시킴
                        .build())
                .build();
    }
//    @Override
//    @Transactional
//    public VoiceAnalysisResultRes requestVoiceAnalysis(MultipartFile voiceFile) {
//        // 1. 음성 데이터 파일 검증
//        if (voiceFile == null || voiceFile.isEmpty()) {
//            throw new VoiceException(VoiceExceptionStatus.VOICE_DATA_INSUFFICIENT);
//        }
//
//        // 2. 데이터 부족 검증 로직
////        if (isDataInsufficient(voiceFile)) {
////            throw new VoiceException(VoiceExceptionStatus.VOICE_DATA_INSUFFICIENT);
////        }
//
//        Path tempPath = null;
//        try {
//            // 3. 임시 파일 생성 및 Python 서버 API 호출 준비
//            tempPath = Files.createTempFile("voice", ".mp3");
//            File tempFile = tempPath.toFile();
//            voiceFile.transferTo(tempFile);
//
//            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
//            body.add("voiceFile", new FileSystemResource(tempFile));
//
//            // 4. Python 서버 API 호출
//            VoiceAnalysisResultRes response = pythonWebClient.post()
//                    .uri("/voice-analysis")
//                    .contentType(MediaType.MULTIPART_FORM_DATA)
//                    .bodyValue(body)
//                    .retrieve()
//                    .bodyToMono(VoiceAnalysisResultRes.class)
//                    .block();
//
//            // 5. 분석 완료 시 처리 로직
//            if (response != null && "COMPLETED".equals(response.getStatus())) {
//                Long recordId = response.getAnalysisId();
//                PracticeRecord practiceRecord = practiceRepository.findById(recordId)
//                        .orElseThrow(() -> new IllegalArgumentException("해당하는 연습 기록이 없습니다."));
//
//                // 분석 결과 엔티티 생성 및 저장
//                AnalysisResult analysisResult = AnalysisResult.builder()
//                        .recordId(recordId)
//                        .practiceRecord(practiceRecord)
//                        .avgWpm(response.getAvgWpm())
//                        .avgPitch(response.getAvgPitch())
//                        .build();
//
//                analysisResultRepository.save(analysisResult);
//
//                // 6. User 엔티티의 최초 기본 음색 저장 로직 (최초 1회만 저장)
//                User user = practiceRecord.getUser();
//
//                // 두 항목이 모두 null일 때만 업데이트하도록 변경
//                if (user.getDefaultVoice() == null ||
//                        (user.getDefaultVoice().getDefaultPitch() == null && user.getDefaultVoice().getDefaultWpm() == null)) {
//
//                    user.updateDefaultVoiceMetrics(response.getAvgPitch(), response.getAvgWpm());
//                    userRepository.save(user);
//                }
//            }
//
//            return response;
//
//        } catch (WebClientResponseException e) {
//            if (e.getStatusCode().value() == 422) {
//                throw new VoiceException(VoiceExceptionStatus.VOICE_UNPROCESSABLE);
//            }
//            throw new VoiceException(VoiceExceptionStatus.VOICE_DATA_INSUFFICIENT);
//        } catch (WebClientRequestException e) {
//            // 7. 네트워크 예외에 대한 일관된 예외 처리
//            throw new VoiceException(VoiceExceptionStatus.VOICE_DATA_INSUFFICIENT);
//        } catch (IOException e) {
//            throw new VoiceException(VoiceExceptionStatus.VOICE_UNPROCESSABLE);
//        } finally {
//            // 8. 예외 발생 여부와 상관없이 임시 파일 삭제를 보장하는 블록
//            if (tempPath != null) {
//                try {
//                    Files.deleteIfExists(tempPath);
//                } catch (IOException e) {
//                    // 예외 무시
//                }
//            }
//        }
//    }

    private boolean isDataInsufficient(MultipartFile file) {
        // 파일의 크기가 최소 요구사항에 미치지 못하는지 확인
        return file.getSize() < 50 * 1024;
    }

    @Override
    @Transactional(readOnly = true)
    public VoiceAnalysisResultRes getVoiceAnalysisResult(Long analysisId) {
        // [임시 확인용] 만약 1번 ID로 요청이 들어올 때 DB 조회를 우회하고 더미 객체를 반환하도록 테스트 가능
        if (analysisId == 1L) {
            return VoiceAnalysisResultRes.builder()
                    .analysisId(1L)
                    .status("COMPLETED")
                    .progress(100)
                    .voiceStyle(VoiceAnalysisResultRes.VoiceStyle.builder()
                            .mostSimilarStyle("신중한형")
                            .matchingRate(92)
                            .description("테스트 님의 목소리는 신뢰감을 주는 중저음 톤으로, '신중한 형' 스타일과 92% 일치합니다.")
                            .build())
                    .userAverageMetrics(VoiceAnalysisResultRes.UserAverageMetrics.builder()
                            .avgPitch(200.0)
                            .avgWPM(150.0)
                            .build())
                    .build();
        }

        // 실제 분석 결과 조회
        AnalysisResult analysisResult = analysisResultRepository.findById(analysisId)
                .orElseThrow(() -> new VoiceException(VoiceExceptionStatus.VOICE_DATA_INSUFFICIENT));

        // DTO 구조에 맞게 응답 생성
        return VoiceAnalysisResultRes.builder()
                .analysisId(analysisResult.getRecordId())
                .status("COMPLETED")
                .progress(100)
                .voiceStyle(VoiceAnalysisResultRes.VoiceStyle.builder()
                        .mostSimilarStyle("신중한형")
                        .matchingRate(92)
                        .description(String.format("%s 님의 목소리는 신뢰감을 주는 중저음 톤으로, '신중한 형' 스타일과 92%% 일치합니다.", analysisResult.getPracticeRecord().getUser().getNickname()))
                        .build())
                .userAverageMetrics(VoiceAnalysisResultRes.UserAverageMetrics.builder()
                        .avgPitch(analysisResult.getAvgPitch())
                        .avgWPM(analysisResult.getAvgWpm()) // AnalysisResult의 WPM 필드명과 매핑
                        .build())
                .build();
    }
}
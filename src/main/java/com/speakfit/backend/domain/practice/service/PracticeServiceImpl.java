package com.speakfit.backend.domain.practice.service;

import com.speakfit.backend.domain.practice.dto.req.AnalyzePracticeReq;
import com.speakfit.backend.domain.practice.dto.req.StartPracticeReq;
import com.speakfit.backend.domain.practice.dto.req.StopPracticeReq;
import com.speakfit.backend.domain.practice.dto.res.*;
import com.speakfit.backend.domain.practice.entity.AiAnalysisResult;
import com.speakfit.backend.domain.practice.entity.AnalysisResult;
import com.speakfit.backend.domain.practice.entity.PracticeRecord;
import com.speakfit.backend.domain.practice.enums.PracticeStatus;
import com.speakfit.backend.domain.practice.exception.PracticeErrorCode;
import com.speakfit.backend.domain.practice.repository.AiAnalysisResultRepository;
import com.speakfit.backend.domain.practice.repository.AnalysisResultRepository;
import com.speakfit.backend.domain.practice.repository.PracticeRepository;
import com.speakfit.backend.domain.script.entity.Script;
import com.speakfit.backend.domain.script.exception.ScriptErrorCode;
import com.speakfit.backend.domain.script.repository.ScriptRepository;
import com.speakfit.backend.domain.user.entity.User;
import com.speakfit.backend.domain.user.repository.UserRepository;
import com.speakfit.backend.global.apiPayload.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PracticeServiceImpl implements PracticeService {

    private final PracticeRepository practiceRepository;
    private final ScriptRepository scriptRepository;
    private final UserRepository userRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final AiAnalysisResultRepository aiAnalysisResultRepository;

    private final WebClient webClient;
    private final AiAnalysisService aiAnalysisService;// 비동기 설계를 위해 같은 클래스 말고 외부에 클래스만듬

    // 발표 연습 시작 서비스 구현

    @Override
    @Transactional
    public StartPracticeRes startPractice(StartPracticeReq.Request req, Long userId) {

        // 1. 사용자 조회

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ScriptErrorCode.SCRIPT_USER_NOT_FOUND));

        // 2. 발표 대본 조회
        Script script = scriptRepository.findById(req.getScriptId())
                .orElseThrow(() -> new CustomException(ScriptErrorCode.SCRIPT_NOT_FOUND));

        // 3. 사용자 권한 체크
        if (!script.getUser().getId().equals(userId)) {
            throw new CustomException(ScriptErrorCode.SCRIPT_ACCESS_DENIED);
        }

        // 4. 연습 기록 생성
        PracticeRecord practiceRecord = PracticeRecord.builder()
                .user(user)
                .script(script)
                .status(PracticeStatus.RECORDING)
                .time(0.0)
                .audioUrl(null)
                .build();

        // 5. DB에 저장
        PracticeRecord savedRecord = practiceRepository.save(practiceRecord);

        // 6. Entity -> DTO 변환 및 반환
        return StartPracticeRes.builder()
                .practiceId(savedRecord.getId())
                .status(savedRecord.getStatus().toString())
                .build();

    }

    // 발표 연습 종료 서비스 구현
    @Override
    @Transactional
    public StopPracticeRes stopPractice(Long practiceId, StopPracticeReq.StopPracticeRequest req, Long userId) {
        // 1. 발표 연습 기록 조회
        PracticeRecord practiceRecord = practiceRepository.findById(practiceId)
                .orElseThrow(() -> new CustomException(PracticeErrorCode.PRACTICE_NOT_FOUND));

        // 2. 사용자 접근 권한 체크
        if (!practiceRecord.getUser().getId().equals(userId)) {
            throw new CustomException(PracticeErrorCode.PRACTICE_ACCESS_DENIED);
        }

        // 3. DTO에서 파일을 꺼내서 업로드
        String audioUrl = uploadAudioFile(practiceId, req.getAudio());

        // 4. db에 저장
        practiceRecord.stopPractice(audioUrl, req.getTime());

        // 5. Entity -> DTO 변환 및 반환
        return StopPracticeRes.builder()
                .practiceId(practiceRecord.getId())
                .time(practiceRecord.getTime())
                .status(practiceRecord.getStatus().toString())
                .build();
    }

    // 발표 연습 종료 음성파일 업로드 메소드 [로컬 저장 로직]
    // 추후 인프라 설계 시 수정예정
    private String uploadAudioFile(Long practiceId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }

        try {
            // 1. 저장 디렉토리 설정 (절대 경로로 변환하여 기준점 확보)
            Path uploadDirPath = Paths.get("uploads/audio/").toAbsolutePath().normalize();
            if (!Files.exists(uploadDirPath)) {
                Files.createDirectories(uploadDirPath);
            }

            // 2. 파일명 생성 및 보안 정제 (Sanitize)
            // file.getOriginalFilename()에 포함될 수 있는 "../../" 등을 제거합니다.
            String extension = getFileExtension(file.getOriginalFilename());
            String fileName = practiceId + extension;

            // 3. 경로 결합 및 정규화 (.normalize())
            Path filePath = uploadDirPath.resolve(fileName).normalize();

            // 최종 경로가 반드시 의도한 디렉토리 내부인지 검증
            if (!filePath.startsWith(uploadDirPath)) {
                throw new RuntimeException("보안 위험이 감지되었습니다: 올바르지 않은 파일 경로입니다.");
            }

            // 4. 실제 파일 저장
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            return filePath.toString();

        } catch (IOException e) {
            throw new RuntimeException("로컬 파일 저장 중 오류가 발생했습니다.", e);
        }
    }

    // 확장자 추출 로직
    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) return "";

        // Paths.get(...).getFileName()은 경로 구분자(/, \)를 무시하고 실제 '파일명'만 남김
        String cleanFileName = Paths.get(fileName).getFileName().toString();

        int dotIdx = cleanFileName.lastIndexOf(".");
        return (dotIdx != -1) ? cleanFileName.substring(dotIdx) : "";
    }

    // 발표 분석 요청 서비스 구현
    @Override
    @Transactional
    public AnalyzePracticeRes analyzePractice(AnalyzePracticeReq.Request request, Long userId) {
        // 1. 연습 기록 조회: 요청된 ID로 기록을 찾고 없으면 예외 발생
        PracticeRecord practiceRecord = practiceRepository.findById(request.getPracticeId())
                .orElseThrow(() -> new CustomException(PracticeErrorCode.PRACTICE_NOT_FOUND));

        // 2. 권한 체크: 현재 사용자가 해당 연습 기록의 소유자인지 검증
        if (!practiceRecord.getUser().getId().equals(userId)) {
            throw new CustomException(PracticeErrorCode.PRACTICE_ACCESS_DENIED);
        }

        // 3. 파일 존재 여부 확인: 분석할 오디오 URL이 비어있거나 null인지 체크
        if (practiceRecord.getAudioUrl() == null || practiceRecord.getAudioUrl().isEmpty()) {
            throw new CustomException(PracticeErrorCode.PRACTICE_AUDIOURL_NOT_FOUND);
        }

        // 4. 상태 변경: 분석 시작 상태로 변경하고 DB에 즉시 반영
        practiceRecord.startAnalysis();
        practiceRepository.save(practiceRecord);

        // 5. 비동기 분석 호출: 파이썬 서버 통신 및 결과 저장을 별도 스레드에서 수행 (Non-blocking)
        aiAnalysisService.processAnalysisAsync(practiceRecord.getId(), practiceRecord.getAudioUrl());

        // 6. 즉시 응답 반환: 사용자는 대기 없이 분석 중이라는 메시지를 받음
        return AnalyzePracticeRes.builder()
                .practiceId(practiceRecord.getId())
                .status("ANALYZING")
                .message("분석 요청이 접수되었습니다.")
                .build();
    }

    // 발표 분석 결과 조회 서비스 구현
    @Override
    public GetPracticeReportRes getPracticeReport(Long practiceId, Long userId) {
        // 1. 연습 기록 조회
        PracticeRecord practiceRecord = practiceRepository.findById(practiceId)
                .orElseThrow(() -> new CustomException(PracticeErrorCode.PRACTICE_NOT_FOUND));

        // 2. 권한 확인
        if (!practiceRecord.getUser().getId().equals(userId)) {
            throw new CustomException(PracticeErrorCode.PRACTICE_ACCESS_DENIED);
        }

        // 3. 상태 체크 (Polling 대응)
        if (practiceRecord.getStatus() != PracticeStatus.ANALYZED) {
            String msg = (practiceRecord.getStatus() == PracticeStatus.ANALYZING)
                    ? "AI가 데이터를 분석하고 있습니다. 잠시만 기다려주세요."
                    : "분석 요청이 처리되지 않았거나 실패했습니다.";

            return GetPracticeReportRes.builder()
                    .practiceId(practiceRecord.getId())
                    .status(practiceRecord.getStatus().toString())
                    .message(msg)
                    .build();
        }

        // 4. 분석 완료 시 데이터 조회
        AnalysisResult analysis = analysisResultRepository.findByPracticeRecord(practiceRecord)
                .orElseThrow(() -> new CustomException(PracticeErrorCode.PRACTICE_NOT_FOUND));

        AiAnalysisResult aiResult = aiAnalysisResultRepository.findByPracticeRecord(practiceRecord)
                .orElseThrow(() -> new CustomException(PracticeErrorCode.PRACTICE_NOT_FOUND));

        // 5. DTO 매핑
        return GetPracticeReportRes.builder()
                .practiceId(practiceRecord.getId())
                .status("ANALYZED")
                .audioUrl(practiceRecord.getAudioUrl())
                .time(practiceRecord.getTime())
                .createdAt(practiceRecord.getCreatedAt())
                .analysis(GetPracticeReportRes.AnalysisDetail.builder()
                        .wpm(GetPracticeReportRes.StatInfo.builder()
                                .avg(analysis.getAvgWpm())
                                .variability(analysis.getWpmDiff()) // diff -> variability 매핑
                                .build())
                        .pitch(GetPracticeReportRes.StatInfo.builder()
                                .avg(analysis.getAvgPitch())
                                .variability(analysis.getPitchDiff())
                                .build())
                        .intensity(GetPracticeReportRes.StatInfo.builder()
                                .avg(analysis.getAvgIntensity())
                                .variability(analysis.getIntensityDiff())
                                .build())
                        .zcr(GetPracticeReportRes.StatInfo.builder()
                                .avg(analysis.getAvgZcr())
                                .variability(analysis.getZcrDiff())
                                .build())
                        .pause(GetPracticeReportRes.PauseInfo.builder()
                                .ratio(analysis.getPauseRatio())
                                .count(analysis.getPauseCount())
                                .build())
                        .build())
                .aiAnalysis(GetPracticeReportRes.AiAnalysisDetail.builder()
                        .summary(aiResult.getAiSummary()) // aiSummary -> summary 매핑
                        .createdAt(aiResult.getCreatedAt())
                        .build())
                .build();

    }
}

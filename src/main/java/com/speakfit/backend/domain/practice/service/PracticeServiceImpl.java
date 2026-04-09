package com.speakfit.backend.domain.practice.service;

import com.speakfit.backend.domain.practice.dto.req.StartPracticeReq;
import com.speakfit.backend.domain.practice.dto.req.StopPracticeReq;
import com.speakfit.backend.domain.practice.dto.res.*;
import com.speakfit.backend.domain.practice.entity.*;
import com.speakfit.backend.domain.practice.enums.Status;
import com.speakfit.backend.domain.practice.exception.PracticeErrorCode;
import com.speakfit.backend.domain.practice.repository.*;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PracticeServiceImpl implements PracticeService {

    private final PracticeRepository practiceRepository;
    private final ScriptRepository scriptRepository;
    private final UserRepository userRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final AiAnalysisResultRepository aiAnalysisResultRepository;
    private final AiAnalysisService aiAnalysisService;

    // 발표 연습 시작 서비스 구현
    @Override
    @Transactional
    public StartPracticeRes startPractice(StartPracticeReq.Request req, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ScriptErrorCode.SCRIPT_USER_NOT_FOUND));

        Script script = scriptRepository.findById(req.getScriptId())
                .orElseThrow(() -> new CustomException(ScriptErrorCode.SCRIPT_NOT_FOUND));

        if (!script.getUser().getId().equals(userId)) {
            throw new CustomException(ScriptErrorCode.SCRIPT_ACCESS_DENIED);
        }

        PracticeRecord practiceRecord = PracticeRecord.builder()
                .user(user)
                .script(script)
                .status(Status.RECORDING) // Status enum 적용
                .time(0.0)
                .audioUrl(null)
                .build();

        PracticeRecord savedRecord = practiceRepository.save(practiceRecord);

        List<StartPracticeRes.ContentRes> contentList = parseMarkedContent(script.getMarkedContent());
        String webSocketUrl = "ws://api.speakfit.com/ws/practice/" + savedRecord.getId();

        return StartPracticeRes.builder()
                .practiceId(savedRecord.getId())
                .title(script.getTitle())
                .webSocketUrl(webSocketUrl)
                .status(savedRecord.getStatus()) // Enum 그대로 전달
                .contentList(contentList)
                .createdAt(savedRecord.getCreatedAt())
                .build();
    }

    private List<StartPracticeRes.ContentRes> parseMarkedContent(String markedContent) {
        List<StartPracticeRes.ContentRes> list = new java.util.ArrayList<>();
        if (markedContent == null || markedContent.isEmpty()) return list;

        String[] tokens = markedContent.split("\\s+");
        int index = 0;
        for (String token : tokens) {
            boolean hasBreak = token.contains("/");
            boolean isEmphasis = token.contains("*");
            String cleanWord = token.replace("/", "").replace("*", "");

            if (!cleanWord.isEmpty()) {
                list.add(StartPracticeRes.ContentRes.builder()
                        .index(index++)
                        .word(cleanWord)
                        .hasBreak(hasBreak)
                        .isEmphasis(isEmphasis)
                        .build());
            }
        }
        return list;
    }

    // 발표 연습 종료 서비스 구현
    @Override
    @Transactional
    public StopPracticeRes stopPractice(Long practiceId, StopPracticeReq.Request req, Long userId) {
        PracticeRecord practiceRecord = practiceRepository.findById(practiceId)
                .orElseThrow(() -> new CustomException(PracticeErrorCode.PRACTICE_NOT_FOUND));

        if (!practiceRecord.getUser().getId().equals(userId)) {
            throw new CustomException(PracticeErrorCode.PRACTICE_ACCESS_DENIED);
        }

        String audioUrl = uploadAudioFile(practiceId, req.getAudio());

        practiceRecord.stopRecording(audioUrl, req.getTime());
        practiceRecord.updateStatus(Status.ANALYZING); 

        aiAnalysisService.processAnalysisAsync(practiceRecord.getId(), audioUrl);

        return StopPracticeRes.builder()
                .practiceId(practiceRecord.getId())
                .status(practiceRecord.getStatus()) // Enum 그대로 전달
                .audioUrl(practiceRecord.getAudioUrl())
                .build();
    }

    // 발표 분석 요청 기능 삭제 (stopPractice에 통합됨)

    @Override
    public GetPracticeReportRes getPracticeReport(Long practiceId, Long userId) {
        PracticeRecord practiceRecord = practiceRepository.findById(practiceId)
                .orElseThrow(() -> new CustomException(PracticeErrorCode.PRACTICE_NOT_FOUND));

        if (!practiceRecord.getUser().getId().equals(userId)) {
            throw new CustomException(PracticeErrorCode.PRACTICE_ACCESS_DENIED);
        }

        if (practiceRecord.getStatus() != Status.ANALYZED) {
            String msg = (practiceRecord.getStatus() == Status.ANALYZING)
                    ? "AI가 데이터를 분석하고 있습니다. 잠시만 기다려주세요."
                    : "분석 요청이 처리되지 않았거나 실패했습니다.";

            return GetPracticeReportRes.builder()
                    .practiceId(practiceRecord.getId())
                    .status(practiceRecord.getStatus())
                    .message(msg)
                    .build();
        }

        AnalysisResult analysis = analysisResultRepository.findByPracticeRecord(practiceRecord)
                .orElseThrow(() -> new CustomException(PracticeErrorCode.PRACTICE_NOT_FOUND));

        AiAnalysisResult aiResult = aiAnalysisResultRepository.findByPracticeRecord(practiceRecord)
                .orElseThrow(() -> new CustomException(PracticeErrorCode.PRACTICE_NOT_FOUND));

        return GetPracticeReportRes.builder()
                .practiceId(practiceRecord.getId())
                .status(Status.ANALYZED)
                .audioUrl(practiceRecord.getAudioUrl())
                .time(practiceRecord.getTime())
                .createdAt(practiceRecord.getCreatedAt())
                .analysis(GetPracticeReportRes.AnalysisDetail.builder()
                        .wpm(GetPracticeReportRes.StatInfo.builder()
                                .avg(analysis.getAvgWpm())
                                .variability(analysis.getWpmDiff())
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
                        .summary(aiResult.getAiSummary())
                        .createdAt(aiResult.getCreatedAt())
                        .build())
                .build();
    }

    private String uploadAudioFile(Long practiceId, MultipartFile file) {
        if (file == null || file.isEmpty()) return null;
        try {
            Path uploadDirPath = Paths.get("uploads/audio/").toAbsolutePath().normalize();
            if (!Files.exists(uploadDirPath)) Files.createDirectories(uploadDirPath);
            String extension = getFileExtension(file.getOriginalFilename());
            String fileName = practiceId + extension;
            Path filePath = uploadDirPath.resolve(fileName).normalize();
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
            return filePath.toString();
        } catch (IOException e) {
            throw new RuntimeException("로컬 파일 저장 중 오류가 발생했습니다.", e);
        }
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) return "";
        String cleanFileName = Paths.get(fileName).getFileName().toString();
        int dotIdx = cleanFileName.lastIndexOf(".");
        return (dotIdx != -1) ? cleanFileName.substring(dotIdx) : "";
    }
}

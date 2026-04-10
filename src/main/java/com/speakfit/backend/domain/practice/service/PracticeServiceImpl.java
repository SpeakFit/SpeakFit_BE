package com.speakfit.backend.domain.practice.service;

import com.speakfit.backend.domain.practice.dto.req.InputPracticeInfoReq;
import com.speakfit.backend.domain.practice.dto.req.SelectStyleReq;
import com.speakfit.backend.domain.practice.dto.req.StopPracticeReq;
import com.speakfit.backend.domain.practice.dto.res.*;
import com.speakfit.backend.domain.practice.entity.*;
import com.speakfit.backend.domain.practice.enums.Status;
import com.speakfit.backend.domain.practice.exception.PracticeErrorCode;
import com.speakfit.backend.domain.practice.repository.*;
import com.speakfit.backend.domain.script.entity.Script;
import com.speakfit.backend.domain.script.exception.ScriptErrorCode;
import com.speakfit.backend.domain.script.repository.ScriptRepository;
import com.speakfit.backend.domain.style.entity.SpeechStyle;
import com.speakfit.backend.domain.style.repository.SpeechStyleRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PracticeServiceImpl implements PracticeService {

    private final PracticeRepository practiceRepository;
    private final ScriptRepository scriptRepository;
    private final UserRepository userRepository;
    private final SpeechStyleRepository speechStyleRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final AiAnalysisResultRepository aiAnalysisResultRepository;
    private final PracticeIssueRepository practiceIssueRepository;
    private final PracticeDetailRepository practiceDetailRepository;
    private final AiAnalysisService aiAnalysisService;

    // 발표 연습 정보값 입력 및 스타일 추천 서비스 구현
    @Override
    @Transactional
    public InputPracticeInfoRes.Response inputPracticeInfo(Long scriptId, InputPracticeInfoReq.Request req, Long userId) {
        // 1. 사용자 및 대본 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ScriptErrorCode.SCRIPT_USER_NOT_FOUND));

        Script script = scriptRepository.findById(scriptId)
                .orElseThrow(() -> new CustomException(ScriptErrorCode.SCRIPT_NOT_FOUND));

        // 2. 연습 기록(PracticeRecord) 초기 생성 (Style은 NULL, READY 상태)
        PracticeRecord practiceRecord = PracticeRecord.builder()
                .user(user)
                .script(script)
                .audienceType(req.getAudienceType())
                .audienceUnderstanding(req.getAudienceUnderstanding())
                .speechInformation(req.getSpeechInformation())
                .status(Status.READY)
                .time(0.0)
                .build();

        PracticeRecord savedRecord = practiceRepository.save(practiceRecord);

        // 3. 발표 스타일 추천 로직 (현재는 DB의 첫 번째 데이터를 추천하는 방식)
        // TODO: 향후 AI를 통한 정밀 추천 로직 연동
        SpeechStyle recommendedStyle = speechStyleRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new CustomException(PracticeErrorCode.PRACTICE_NOT_FOUND));

        // 4. 생성된 연습 ID와 추천 스타일 반환
        return InputPracticeInfoRes.Response.builder()
                .practiceId(savedRecord.getId())
                .recommendedStyle(InputPracticeInfoRes.RecommendedStyle.builder()
                        .styleId(recommendedStyle.getId())
                        .styleType(recommendedStyle.getStyleType())
                        .description(recommendedStyle.getDescription())
                        .build())
                .build();
    }

    // 추천 또는 선택한 발표 스타일 확정 및 낭독 기호 생성 서비스 구현
    @Override
    @Transactional
    public void selectStyle(Long practiceId, SelectStyleReq.Request req, Long userId) {
        // 1. 연습 기록 조회 및 권한 체크
        PracticeRecord record = practiceRepository.findById(practiceId)
                .orElseThrow(() -> new CustomException(PracticeErrorCode.PRACTICE_NOT_FOUND));

        if (!record.getUser().getId().equals(userId)) {
            throw new CustomException(PracticeErrorCode.PRACTICE_ACCESS_DENIED);
        }

        // 2. 선택한 스타일 조회 및 업데이트
        SpeechStyle style = speechStyleRepository.findById(req.getStyleId())
                .orElseThrow(() -> new CustomException(PracticeErrorCode.PRACTICE_NOT_FOUND));
        
        record.selectStyle(style);

        // 3. AI를 통해 낭독 기호 대본 생성 및 Script 업데이트
        String markedContent = aiAnalysisService.generateMarkedContent(record.getScript(), style, record);
        if (markedContent != null) {
            record.getScript().updateMarkedContent(markedContent);
            scriptRepository.save(record.getScript());
        }
    }

    // 발표 연습 시작 (실제 녹음/분석 활성화) 서비스 구현
    @Override
    @Transactional
    public StartPracticeRes startPractice(Long practiceId, Long userId) {
        // 1. 연습 기록 조회 및 권한 체크
        PracticeRecord record = practiceRepository.findById(practiceId)
                .orElseThrow(() -> new CustomException(PracticeErrorCode.PRACTICE_NOT_FOUND));

        if (!record.getUser().getId().equals(userId)) {
            throw new CustomException(PracticeErrorCode.PRACTICE_ACCESS_DENIED);
        }

        // 2. 연습 상태를 RECORDING으로 변경
        record.updateStatus(Status.RECORDING);

        // 3. 실시간 분석을 위한 웹소켓 URL 및 대본 단어 리스트 구성
        List<StartPracticeRes.ContentRes> contentList = parseMarkedContent(record.getScript().getMarkedContent());
        String webSocketUrl = "ws://api.speakfit.com/ws/practice/" + record.getId();

        // 4. 시작 정보 반환
        return StartPracticeRes.builder()
                .practiceId(record.getId())
                .title(record.getScript().getTitle())
                .webSocketUrl(webSocketUrl)
                .status(record.getStatus())
                .contentList(contentList)
                .createdAt(record.getCreatedAt())
                .build();
    }

    // 발표 연습 종료 및 분석 트리거 서비스 구현
    @Override
    @Transactional
    public StopPracticeRes stopPractice(Long practiceId, StopPracticeReq.Request req, Long userId) {
        // 1. 연습 기록 조회 및 권한 체크
        PracticeRecord practiceRecord = practiceRepository.findById(practiceId)
                .orElseThrow(() -> new CustomException(PracticeErrorCode.PRACTICE_NOT_FOUND));

        if (!practiceRecord.getUser().getId().equals(userId)) {
            throw new CustomException(PracticeErrorCode.PRACTICE_ACCESS_DENIED);
        }

        // 2. 전체 녹음 파일 업로드
        String audioUrl = uploadAudioFile(practiceId, req.getAudio());

        // 3. 상태 변경 (ANALYZING) 및 결과 기록
        practiceRecord.stopRecording(audioUrl, req.getTime());
        practiceRecord.updateStatus(Status.ANALYZING); 

        // 4. 비동기 AI 분석 프로세스 시작
        aiAnalysisService.processAnalysisAsync(practiceRecord.getId(), audioUrl);

        return StopPracticeRes.builder()
                .practiceId(practiceRecord.getId())
                .status(practiceRecord.getStatus())
                .audioUrl(practiceRecord.getAudioUrl())
                .build();
    }

    // 발표 연습 결과 리포트 조회 서비스 구현
    @Override
    public GetPracticeReportRes getPracticeReport(Long practiceId, Long userId) {
        // 1. 연습 기록 조회 및 권한 확인
        PracticeRecord record = practiceRepository.findById(practiceId)
                .orElseThrow(() -> new CustomException(PracticeErrorCode.PRACTICE_NOT_FOUND));

        if (!record.getUser().getId().equals(userId)) {
            throw new CustomException(PracticeErrorCode.PRACTICE_ACCESS_DENIED);
        }

        // 2. 분석 미완료 시 폴링 메시지 반환
        if (record.getStatus() != Status.ANALYZED) {
            return GetPracticeReportRes.builder()
                    .practiceId(record.getId())
                    .status(record.getStatus())
                    .message(record.getStatus() == Status.ANALYZING ? "분석 중입니다." : "분석 실패")
                    .build();
        }

        // 3. 모든 분석 관련 테이블 데이터 통합 조회
        AnalysisResult analysis = analysisResultRepository.findByPracticeRecord(record).orElseThrow();
        AiAnalysisResult aiResult = aiAnalysisResultRepository.findByPracticeRecord(record).orElseThrow();
        List<PracticeIssue> issues = practiceIssueRepository.findAllByPracticeRecordId(record.getId());
        List<PracticeDetail> details = practiceDetailRepository.findAllByPracticeRecordIdOrderByWordIndexAsc(record.getId());

        // 4. 실시간 단어 분석 데이터를 문장 단위로 병합
        List<GetPracticeReportRes.SentenceRes> sentences = mergeDetailsToSentences(record.getScript().getContent(), details);

        // 5. 최종 리포트 DTO 조립 및 반환
        return GetPracticeReportRes.builder()
                .practiceId(record.getId())
                .status(record.getStatus())
                .audioUrl(record.getAudioUrl())
                .time(record.getTime())
                .createdAt(record.getCreatedAt())
                .analysis(GetPracticeReportRes.AnalysisDetail.builder()
                        .wpm(new GetPracticeReportRes.StatInfo(analysis.getAvgWpm(), analysis.getWpmDiff()))
                        .pitch(new GetPracticeReportRes.StatInfo(analysis.getAvgPitch(), analysis.getPitchDiff()))
                        .intensity(new GetPracticeReportRes.StatInfo(analysis.getAvgIntensity(), analysis.getIntensityDiff()))
                        .zcr(new GetPracticeReportRes.StatInfo(analysis.getAvgZcr(), analysis.getZcrDiff()))
                        .pause(new GetPracticeReportRes.PauseInfo(analysis.getPauseRatio(), analysis.getPauseCount()))
                        .build())
                .aiAnalysis(GetPracticeReportRes.AiAnalysisDetail.builder()
                        .aiSummary(aiResult.getAiSummary())
                        .wpmSummary(aiResult.getWpmSummary())
                        .wpmFeedback(aiResult.getWpmFeedback())
                        .energySummary(aiResult.getEnergySummary())
                        .energyFeedback(aiResult.getEnergyFeedback())
                        .pauseFeedback(aiResult.getPauseFeedback())
                        .symbolFeedback(aiResult.getSymbolFeedback())
                        .goalSimilarityScore(aiResult.getGoalSimilarityScore())
                        .goalSummary(aiResult.getGoalSummary())
                        .goalFeedback(aiResult.getGoalFeedback())
                        .createdAt(aiResult.getCreatedAt())
                        .build())
                .practiceIssues(issues.stream().map(i -> GetPracticeReportRes.PracticeIssueRes.builder()
                        .startIndex(i.getStartIndex())
                        .endIndex(i.getEndIndex())
                        .issueSummary(i.getIssueSummary())
                        .feedbackContent(i.getFeedbackContent())
                        .wpm(i.getWpm())
                        .intensity(i.getIntensity())
                        .build()).collect(Collectors.toList()))
                .sentences(sentences)
                .build();
    }

    // 낭독 기호 대본 파싱 헬퍼 메서드
    private List<StartPracticeRes.ContentRes> parseMarkedContent(String markedContent) {
        List<StartPracticeRes.ContentRes> list = new ArrayList<>();
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

    // 단어별 타임스탬프를 문장 단위로 병합하는 헬퍼 메서드
    private List<GetPracticeReportRes.SentenceRes> mergeDetailsToSentences(String fullContent, List<PracticeDetail> details) {
        List<GetPracticeReportRes.SentenceRes> sentenceList = new ArrayList<>();
        if (fullContent == null || details.isEmpty()) return sentenceList;
        String[] rawSentences = fullContent.split("(?<=[.?!])\\s+");
        int currentWordIdx = 0;
        for (int i = 0; i < rawSentences.length; i++) {
            String sentenceText = rawSentences[i];
            String[] wordsInSentence = sentenceText.trim().split("\\s+");
            if (currentWordIdx >= details.size()) break;
            PracticeDetail firstWord = details.get(currentWordIdx);
            PracticeDetail lastWord = details.get(Math.min(currentWordIdx + wordsInSentence.length - 1, details.size() - 1));
            sentenceList.add(GetPracticeReportRes.SentenceRes.builder()
                    .index(i)
                    .text(sentenceText)
                    .startTime(firstWord.getStartTime())
                    .endTime(lastWord.getEndTime())
                    .status(firstWord.getStatus()) 
                    .build());
            currentWordIdx += wordsInSentence.length;
        }
        return sentenceList;
    }

    // 오디오 파일 저장 헬퍼 메서드
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
            throw new RuntimeException("파일 저장 중 오류가 발생했습니다.", e);
        }
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) return "";
        String cleanFileName = Paths.get(fileName).getFileName().toString();
        int dotIdx = cleanFileName.lastIndexOf(".");
        return (dotIdx != -1) ? cleanFileName.substring(dotIdx) : "";
    }
}

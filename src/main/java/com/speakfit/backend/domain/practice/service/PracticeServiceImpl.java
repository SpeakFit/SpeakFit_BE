package com.speakfit.backend.domain.practice.service;

import com.speakfit.backend.domain.practice.dto.req.InputPracticeInfoReq;
import com.speakfit.backend.domain.practice.dto.req.SelectStyleReq;
import com.speakfit.backend.domain.practice.dto.req.StopPracticeReq;
import com.speakfit.backend.domain.practice.dto.res.*;
import com.speakfit.backend.domain.practice.enums.AudienceType;
import com.speakfit.backend.domain.practice.enums.AudienceUnderstanding;
import com.speakfit.backend.domain.practice.enums.SpeechInformation;
import com.speakfit.backend.domain.practice.entity.*;
import com.speakfit.backend.domain.practice.enums.Status;
import com.speakfit.backend.domain.practice.exception.PracticeErrorCode;
import com.speakfit.backend.domain.practice.repository.*;
import com.speakfit.backend.domain.script.entity.Script;
import com.speakfit.backend.domain.script.entity.ScriptSentence;
import com.speakfit.backend.domain.script.entity.ScriptWord;
import com.speakfit.backend.domain.script.exception.ScriptErrorCode;
import com.speakfit.backend.domain.script.repository.ScriptRepository;
import com.speakfit.backend.domain.script.service.ScriptContentParser;
import com.speakfit.backend.domain.style.entity.SpeechStyle;
import com.speakfit.backend.domain.style.enums.StyleType;
import com.speakfit.backend.domain.style.exception.SpeechStyleErrorCode;
import com.speakfit.backend.domain.style.repository.SpeechStyleRepository;
import com.speakfit.backend.domain.user.entity.User;
import com.speakfit.backend.domain.user.repository.UserRepository;
import com.speakfit.backend.global.apiPayload.exception.CustomException;
import com.speakfit.backend.global.infra.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PracticeServiceImpl implements PracticeService {

    private final PracticeRepository practiceRepository;
    private final ScriptRepository scriptRepository;
    private final UserRepository userRepository;
    private final SpeechStyleRepository speechStyleRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final AiAnalysisResultRepository aiAnalysisResultRepository;
    private final PracticeIssueRepository practiceIssueRepository;
    private final PracticeDetailRepository practiceDetailRepository;
    private final PracticeSentenceResultRepository practiceSentenceResultRepository;
    private final AiAnalysisService aiAnalysisService;
    private final PracticeTxService practiceTxService;
    private final ScriptContentParser scriptContentParser;
    private final JwtProvider jwtProvider;

    @Value("${app.websocket.base-url}")
    private String webSocketBaseUrl;

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
        StyleType recommendedStyleType = recommendStyleType(req);
        List<SpeechStyle> styles = speechStyleRepository.findAllByOrderByIdAsc();

        if (styles.isEmpty()) {
            throw new CustomException(SpeechStyleErrorCode.STYLES_EMPTY);
        }

        List<InputPracticeInfoRes.StyleItem> styleList = styles.stream()
                .map(style -> InputPracticeInfoRes.StyleItem.builder()
                        .styleId(style.getId())
                        .styleType(style.getStyleType())
                        .description(style.getDescription())
                        .guideAudioUrl(style.getSampleAudioUrl())
                        .isRecommended(style.getStyleType() == recommendedStyleType)
                        .build())
                .toList();

        // 4. 생성된 연습 ID와 추천 스타일 반환
        return InputPracticeInfoRes.Response.builder()
                .practiceId(savedRecord.getId())
                .styleList(styleList)
                .build();
    }

    // 추천 또는 선택한 발표 스타일 확정 및 낭독 가이드 반환 서비스 구현
    private StyleType recommendStyleType(InputPracticeInfoReq.Request req) {
        if (req.getSpeechInformation() == SpeechInformation.INTERVIEW) {
            return StyleType.CALM_LOW_TONE;
        }

        if (req.getSpeechInformation() == SpeechInformation.LECTURE
                || req.getAudienceUnderstanding() == AudienceUnderstanding.LOW) {
            return StyleType.STANDARD_LECTURE;
        }

        if (req.getSpeechInformation() == SpeechInformation.DISCUSSION
                || req.getSpeechInformation() == SpeechInformation.FEEDBACKPRACTICE) {
            return StyleType.DELIVERY;
        }

        if (req.getAudienceType() == AudienceType.CHILD
                || req.getAudienceType() == AudienceType.YOUTH) {
            return StyleType.ENERGETIC_FAST;
        }

        return StyleType.ENERGETIC_FAST;
    }

    @Override
    public SelectStyleRes.Response selectStyle(Long practiceId, SelectStyleReq.Request req, Long userId) {
        // 1. 연습 기록 조회 및 권한 체크 (조회는 트랜잭션 없이 수행)
        PracticeRecord record = practiceRepository.findById(practiceId)
                .orElseThrow(() -> new CustomException(PracticeErrorCode.PRACTICE_NOT_FOUND));

        if (!record.getUser().getId().equals(userId)) {
            throw new CustomException(PracticeErrorCode.PRACTICE_ACCESS_DENIED);
        }

        // 2. 선택한 스타일 조회
        SpeechStyle style = speechStyleRepository.findById(req.getStyleId())
                .orElseThrow(() -> new CustomException(SpeechStyleErrorCode.STYLE_NOT_FOUND));

        // 3. 스타일 확정 (DB 작업만 트랜잭션으로 처리)
        practiceTxService.updateStyle(practiceId, style.getId());

        // 4. 낭독 가이드(contentList) 생성 및 반환
        // 대본에 이미 저장된 markedContent를 사용
        List<SelectStyleRes.ContentRes> contentList = parseMarkedContentToSelectStyleRes(record.getScript().getMarkedContent());

        return SelectStyleRes.Response.builder()
                .practiceId(record.getId())
                .styleType(style.getStyleType())
                .contentList(contentList)
                .build();
    }

    private List<SelectStyleRes.ContentRes> parseMarkedContentToSelectStyleRes(String markedContent) {
        List<SelectStyleRes.ContentRes> list = new ArrayList<>();
        if (markedContent == null) return list;

        String[] tokens = markedContent.split("\\s+");
        int index = 0;

        for (String token : tokens) {
            // 단독 기호 처리: 이전 단어의 속성으로 병합
            if ((token.equals("/") || token.equals("*")) && !list.isEmpty()) {
                SelectStyleRes.ContentRes last = list.get(list.size() - 1);
                list.set(list.size() - 1, SelectStyleRes.ContentRes.builder()
                        .index(last.getIndex())
                        .word(last.getWord())
                        .hasBreak(token.equals("/") || last.isHasBreak())
                        .emphasis(token.equals("*") || last.isEmphasis())
                        .build());
                continue;
            }

            boolean hasBreak = token.contains("/");
            boolean isEmphasis = token.contains("*");
            String cleanWord = token.replace("/", "").replace("*", "");

            if (!cleanWord.isEmpty()) {
                list.add(SelectStyleRes.ContentRes.builder()
                        .index(index++)
                        .word(cleanWord)
                        .hasBreak(hasBreak)
                        .emphasis(isEmphasis)
                        .build());
            }
        }
        return list;
    }

    // 발표 연습 시작 (실제 녹음/분석 활성화) 서비스 구현
    @Override
    @Transactional
    public StartPracticeRes.Response startPractice(Long practiceId, Long userId) {
        // 1. 연습 기록 조회 및 권한 체크
        PracticeRecord record = practiceRepository.findById(practiceId)
                .orElseThrow(() -> new CustomException(PracticeErrorCode.PRACTICE_NOT_FOUND));

        if (!record.getUser().getId().equals(userId)) {
            throw new CustomException(PracticeErrorCode.PRACTICE_ACCESS_DENIED);
        }

        // 2. 연습 상태를 RECORDING으로 변경
        record.updateStatus(Status.RECORDING);

        // 3. 실시간 분석을 위한 웹소켓 URL 및 대본 문장/단어 리스트 구성
        List<ScriptSentence> scriptSentences = getOrCreateScriptSentences(record.getScript());
        List<StartPracticeRes.SentenceRes> sentences = toSentenceRes(scriptSentences);
        List<StartPracticeRes.WordRes> scriptWords = sentences.stream()
                .flatMap(sentence -> sentence.getWords().stream())
                .sorted(Comparator.comparing(StartPracticeRes.WordRes::getGlobalWordIndex))
                .toList();
        List<StartPracticeRes.ContentRes> contentList = scriptWords.stream()
                .map(word -> StartPracticeRes.ContentRes.builder()
                        .index(word.getGlobalWordIndex())
                        .word(word.getText())
                        .hasBreak(false)
                        .emphasis(false)
                        .build())
                .toList();
        String webSocketToken = jwtProvider.createPracticeWebSocketToken(userId, record.getId());
        String webSocketUrl = webSocketBaseUrl + record.getId()
                + "?token=" + URLEncoder.encode(webSocketToken, StandardCharsets.UTF_8);

        // 4. 시작 정보 반환
        return StartPracticeRes.Response.builder()
                .practiceId(record.getId())
                .title(record.getScript().getTitle())
                .webSocketUrl(webSocketUrl)
                .status(record.getStatus())
                .contentList(contentList)
                .sentences(sentences)
                .scriptWords(scriptWords)
                .createdAt(record.getCreatedAt())
                .build();
    }

    // 저장된 대본 문장 데이터 조회 또는 백필 구현
    private List<ScriptSentence> getOrCreateScriptSentences(Script script) {
        if (script.getScriptSentences() == null || script.getScriptSentences().isEmpty()) {
            List<ScriptSentence> parsedSentences = scriptContentParser.parse(script.getContent());
            parsedSentences.forEach(script::addScriptSentence);
            scriptRepository.saveAndFlush(script);
        }

        return script.getScriptSentences().stream()
                .sorted(Comparator.comparing(ScriptSentence::getSentenceIndex))
                .toList();
    }

    // 대본 문장 응답 변환 구현
    private List<StartPracticeRes.SentenceRes> toSentenceRes(List<ScriptSentence> scriptSentences) {
        return scriptSentences.stream()
                .map(sentence -> StartPracticeRes.SentenceRes.builder()
                        .scriptSentenceId(sentence.getId())
                        .sentenceIndex(sentence.getSentenceIndex())
                        .originalText(sentence.getOriginalText())
                        .normalizedText(sentence.getNormalizedText())
                        .startCharIndex(sentence.getStartCharIndex())
                        .endCharIndex(sentence.getEndCharIndex())
                        .words(sentence.getScriptWords().stream()
                                .sorted(Comparator.comparing(ScriptWord::getSentenceWordIndex))
                                .map(word -> toWordRes(sentence, word))
                                .toList())
                        .build())
                .toList();
    }

    // 대본 단어 응답 변환 구현
    private StartPracticeRes.WordRes toWordRes(ScriptSentence sentence, ScriptWord word) {
        return StartPracticeRes.WordRes.builder()
                .scriptWordId(word.getId())
                .scriptSentenceId(sentence.getId())
                .sentenceIndex(sentence.getSentenceIndex())
                .globalWordIndex(word.getGlobalWordIndex())
                .sentenceWordIndex(word.getSentenceWordIndex())
                .text(word.getText())
                .normalizedText(word.getNormalizedText())
                .startCharIndex(word.getStartCharIndex())
                .endCharIndex(word.getEndCharIndex())
                .build();
    }

    // 발표 연습 종료 및 분석 트리거 서비스 구현
    @Override
    public StopPracticeRes.Response stopPractice(Long practiceId, StopPracticeReq.Request req, Long userId) {
        // 1. 연습 기록 조회 및 권한 체크
        PracticeRecord practiceRecord = practiceRepository.findById(practiceId)
                .orElseThrow(() -> new CustomException(PracticeErrorCode.PRACTICE_NOT_FOUND));

        if (!practiceRecord.getUser().getId().equals(userId)) {
            throw new CustomException(PracticeErrorCode.PRACTICE_ACCESS_DENIED);
        }

        // 2. 전체 녹음 파일 업로드 (가장 오래 걸리는 I/O를 트랜잭션 밖에서 수행)
        String audioUrl = uploadAudioFile(practiceId, req.getAudio());

        // 3. 상태 변경 및 결과 기록 (DB 작업만 트랜잭션으로 처리)
        practiceTxService.saveStopPracticeResults(practiceId, audioUrl, req.getTime());

        // 4. 비동기 AI 분석 프로세스 시작
        aiAnalysisService.processAnalysisAsync(practiceRecord.getId(), audioUrl);

        return StopPracticeRes.Response.builder()
                .practiceId(practiceRecord.getId())
                .status(Status.ANALYZING)
                .audioUrl(audioUrl)
                .build();
    }

    // 발표 연습 결과 리포트 조회 서비스 구현
    @Override
    @Transactional(readOnly = true)
    public GetPracticeReportRes.Response getPracticeReport(Long practiceId, Long userId) {
        // 1. 연습 기록 조회 및 권한 확인
        PracticeRecord record = practiceRepository.findById(practiceId)
                .orElseThrow(() -> new CustomException(PracticeErrorCode.PRACTICE_NOT_FOUND));

        if (!record.getUser().getId().equals(userId)) {
            throw new CustomException(PracticeErrorCode.PRACTICE_ACCESS_DENIED);
        }

        // 2. 분석 미완료 시 폴링 메시지 반환
        if (record.getStatus() != Status.ANALYZED) {
            return GetPracticeReportRes.Response.builder()
                    .practiceId(record.getId())
                    .status(record.getStatus())
                    .message(record.getStatus() == Status.ANALYZING ? "분석 중입니다." : "분석 실패")
                    .build();
        }

        // 3. 모든 분석 관련 테이블 데이터 통합 조회
        AnalysisResult analysis = analysisResultRepository.findByPracticeRecord(record).orElseThrow();
        AiAnalysisResult aiResult = aiAnalysisResultRepository.findByPracticeRecord(record).orElseThrow();
        List<PracticeIssue> issues = practiceIssueRepository.findAllByPracticeRecordIdOrderByDisplayOrderAscIdAsc(record.getId());
        List<PracticeSentenceResult> sentenceResults = practiceSentenceResultRepository.findAllByPracticeRecordIdOrderBySentenceIndexAsc(record.getId());
        List<PracticeDetail> details = practiceDetailRepository.findAllByPracticeRecordIdOrderByWordIndexAsc(record.getId());

        // 4. 문장 단위 분석 결과 우선 사용 및 기존 데이터 fallback
        List<GetPracticeReportRes.SentenceRes> sentences = buildReportSentences(record, sentenceResults, details);

        // 5. 최종 리포트 DTO 조립 및 반환
        return GetPracticeReportRes.Response.builder()
                .practiceId(record.getId())
                .status(record.getStatus())
                .audioUrl(record.getAudioUrl())
                .time(record.getTime())
                .createdAt(record.getCreatedAt())
                .audienceType(record.getAudienceType())
                .audienceUnderstanding(record.getAudienceUnderstanding())
                .speechInformation(record.getSpeechInformation())
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
                        .scriptSentenceId(i.getScriptSentence() != null ? i.getScriptSentence().getId() : null)
                        .sentenceIndex(i.getSentenceIndex())
                        .startIndex(i.getStartIndex())
                        .endIndex(i.getEndIndex())
                        .issueType(i.getIssueType())
                        .issueSummary(i.getIssueSummary())
                        .feedbackContent(i.getFeedbackContent())
                        .reason(i.getReason())
                        .score(i.getScore())
                        .displayOrder(i.getDisplayOrder())
                        .wpm(i.getWpm())
                        .intensity(i.getIntensity())
                        .build()).collect(Collectors.toList()))
                .sentences(sentences)
                .build();
    }

    // 리포트 문장 목록 구성 구현
    private List<GetPracticeReportRes.SentenceRes> buildReportSentences(PracticeRecord record,
                                                                        List<PracticeSentenceResult> sentenceResults,
                                                                        List<PracticeDetail> details) {
        if (sentenceResults != null && !sentenceResults.isEmpty()) {
            return sentenceResults.stream()
                    .map(this::toReportSentenceRes)
                    .toList();
        }

        return mergeDetailsToSentences(record.getScript().getContent(), details);
    }

    // 문장 단위 분석 결과 응답 변환 구현
    private GetPracticeReportRes.SentenceRes toReportSentenceRes(PracticeSentenceResult sentenceResult) {
        ScriptSentence scriptSentence = sentenceResult.getScriptSentence();

        return GetPracticeReportRes.SentenceRes.builder()
                .scriptSentenceId(scriptSentence != null ? scriptSentence.getId() : null)
                .index(sentenceResult.getSentenceIndex())
                .text(scriptSentence != null ? scriptSentence.getOriginalText() : null)
                .startTime(toSeconds(sentenceResult.getStartMs()))
                .endTime(toSeconds(sentenceResult.getEndMs()))
                .startMs(sentenceResult.getStartMs())
                .endMs(sentenceResult.getEndMs())
                .wordCount(sentenceResult.getWordCount())
                .skippedWordCount(sentenceResult.getSkippedWordCount())
                .wpm(sentenceResult.getWpm())
                .pauseDurationMs(sentenceResult.getPauseDurationMs())
                .avgPitch(sentenceResult.getAvgPitch())
                .avgIntensity(sentenceResult.getAvgIntensity())
                .score(sentenceResult.getScore())
                .status(sentenceResult.getStatus() != null ? sentenceResult.getStatus().name() : null)
                .build();
    }

    // 밀리초를 초 단위로 변환 구현
    private Double toSeconds(Long millis) {
        if (millis == null) {
            return null;
        }

        return millis / 1000.0;
    }

    // 낭독 기호 대본 파싱 헬퍼 메서드
    private List<StartPracticeRes.ContentRes> parseMarkedContent(String markedContent) {
        List<StartPracticeRes.ContentRes> list = new ArrayList<>();
        if (markedContent == null || markedContent.isEmpty()) return list;

        String[] tokens = markedContent.split("\\s+");
        int index = 0;

        for (String token : tokens) {
            // 단독 기호 처리
            if ((token.equals("/") || token.equals("*")) && !list.isEmpty()) {
                StartPracticeRes.ContentRes last = list.get(list.size() - 1);
                list.set(list.size() - 1, StartPracticeRes.ContentRes.builder()
                        .index(last.getIndex())
                        .word(last.getWord())
                        .hasBreak(token.equals("/") || last.isHasBreak())
                        .emphasis(token.equals("*") || last.isEmphasis())
                        .build());
                continue;
            }

            boolean hasBreak = token.contains("/");
            boolean isEmphasis = token.contains("*");
            String cleanWord = token.replace("/", "").replace("*", "");

            if (!cleanWord.isEmpty()) {
                list.add(StartPracticeRes.ContentRes.builder()
                        .index(index++)
                        .word(cleanWord)
                        .hasBreak(hasBreak)
                        .emphasis(isEmphasis)
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
                    .status(firstWord.getStatus().name()) 
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

    // 파일 확장자 추출 구현
    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) return "";
        String cleanFileName = Paths.get(fileName).getFileName().toString();
        int dotIdx = cleanFileName.lastIndexOf(".");
        return (dotIdx != -1) ? cleanFileName.substring(dotIdx) : "";
    }
}

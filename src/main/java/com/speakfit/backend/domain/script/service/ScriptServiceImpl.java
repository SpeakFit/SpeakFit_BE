package com.speakfit.backend.domain.script.service;

import com.speakfit.backend.domain.script.dto.req.AddScriptReq;
import com.speakfit.backend.domain.script.dto.req.AiGenerateScriptReq;
import com.speakfit.backend.domain.script.dto.req.AiUpdateScriptReq;
import com.speakfit.backend.domain.script.dto.req.UpdateScriptReq;
import com.speakfit.backend.domain.script.dto.res.AddScriptRes;
import com.speakfit.backend.domain.script.dto.res.AiGenerateScriptRes;
import com.speakfit.backend.domain.script.dto.res.AiUpdateScriptRes;
import com.speakfit.backend.domain.script.dto.res.UpdateScriptRes;
import com.speakfit.backend.domain.script.dto.res.DeleteScriptRes;
import com.speakfit.backend.domain.script.dto.res.GetScriptDetailRes;
import com.speakfit.backend.domain.script.dto.res.GetScriptListRes;
import com.speakfit.backend.domain.script.dto.res.UploadPptRes;
import com.speakfit.backend.domain.script.entity.Script;
import com.speakfit.backend.domain.script.enums.PptStatus;
import com.speakfit.backend.domain.script.enums.ScriptType;
import com.speakfit.backend.domain.script.exception.ScriptErrorCode;
import com.speakfit.backend.domain.script.repository.ScriptRepository;
import com.speakfit.backend.domain.practice.service.AiAnalysisService;
import com.speakfit.backend.domain.user.repository.UserRepository;
import com.speakfit.backend.global.apiPayload.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class ScriptServiceImpl implements ScriptService {

    private final ScriptRepository scriptRepository;
    private final com.speakfit.backend.domain.practice.repository.PracticeRepository practiceRepository;
    private final AiAnalysisService aiAnalysisService;
    private final ScriptTxService scriptTxService;
    private final UserRepository userRepository;
    /** 단기 API 호출용 (PPT 변환 등 일반 요청) */
    private final WebClient webClient;
    /** [STEP-A] SSE 스트리밍 전용 — responseTimeout 5분 설정 */
    private final WebClient streamingWebClient;
    private final PptConvertAsyncService pptConvertAsyncService;

    public ScriptServiceImpl(
            ScriptRepository scriptRepository,
            com.speakfit.backend.domain.practice.repository.PracticeRepository practiceRepository,
            AiAnalysisService aiAnalysisService,
            ScriptTxService scriptTxService,
            UserRepository userRepository,
            @Qualifier("webClient") WebClient webClient,
            @Qualifier("streamingWebClient") WebClient streamingWebClient,
            PptConvertAsyncService pptConvertAsyncService) {
        this.scriptRepository = scriptRepository;
        this.practiceRepository = practiceRepository;
        this.aiAnalysisService = aiAnalysisService;
        this.scriptTxService = scriptTxService;
        this.userRepository = userRepository;
        this.webClient = webClient;
        this.streamingWebClient = streamingWebClient;
        this.pptConvertAsyncService = pptConvertAsyncService;
    }

    // [STEP-B] 발표 대본 추가 기능 구현 — 낭독기호 동기 처리로 즉시 응답 보장
    // Python 로컬 엔진 사용으로 평균 < 50ms이므로 동기 처리가 사용자 경험에 유리함.
    @Override
    public AddScriptRes.Response addScript(AddScriptReq.Request req, Long userId) {

        if (!userRepository.existsById(userId)) {
            throw new CustomException(ScriptErrorCode.SCRIPT_USER_NOT_FOUND);
        }

        // 1. 저장하기 전에 먼저 낭독기호를 생성합니다 (동기 호출, 로컬 엔진 활용)
        String markedContent = aiAnalysisService.generateMarkedContent(req.getContent());
        if (markedContent == null || markedContent.isBlank()) {
            markedContent = req.getContent();
        }

        // 2. 처음부터 낭독기호가 포함된 내용으로 DB에 저장합니다
        Script savedScript = scriptTxService.saveScript(req, userId, markedContent);

        // 3. 낭독기호가 포함된 결과로 contentList를 구성하여 즉시 반환합니다
        List<AddScriptRes.ContentRes> contentList = parseMarkedContent(markedContent);

        return AddScriptRes.Response.builder()
                .id(savedScript.getId())
                .title(savedScript.getTitle())
                .content(savedScript.getContent())
                .contentList(contentList)
                .createdAt(savedScript.getCreatedAt())
                .build();
    }

    // 낭독 기호 대본 파싱 기능 구현
    private List<AddScriptRes.ContentRes> parseMarkedContent(String markedContent) {
        List<AddScriptRes.ContentRes> list = new ArrayList<>();

        if (markedContent == null || markedContent.isEmpty()) {
            return list;
        }

        String[] tokens = markedContent.split("\\s+");
        int index = 0;

        for (String token : tokens) {
            // 단독 기호를 이전 단어의 속성으로 병합
            if ((token.equals("/") || token.equals("*")) && !list.isEmpty()) {
                AddScriptRes.ContentRes last = list.get(list.size() - 1);
                list.set(list.size() - 1, AddScriptRes.ContentRes.builder()
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
                list.add(AddScriptRes.ContentRes.builder()
                        .index(index++)
                        .word(cleanWord)
                        .hasBreak(hasBreak)
                        .emphasis(isEmphasis)
                        .build());
            }
        }
        return list;
    }

    // 발표 대본 목록 조회 기능 구현
    @Override
    @Transactional(readOnly = true)
    public List<GetScriptListRes.Response> getScripts(Long userId) {
        List<Script> scripts = scriptRepository.findAllByUserId(userId);
        return scripts.stream()
                .map(script -> GetScriptListRes.Response.builder()
                        .id(script.getId())
                        .title(script.getTitle())
                        .content(script.getContent())
                        .scriptType(script.getScriptType())
                        .createdAt(script.getCreatedAt())
                        .build())
                .toList();
    }

    // 발표 대본 상세 조회 기능 구현
    @Override
    @Transactional(readOnly = true)
    public GetScriptDetailRes.Response getScript(Long scriptId, Long userId) {
        Script script = scriptRepository.findByIdWithUser(scriptId)
                .orElseThrow(() -> new CustomException(ScriptErrorCode.SCRIPT_NOT_FOUND));

        if (!script.getUser().getId().equals(userId)) {
            throw new CustomException(ScriptErrorCode.SCRIPT_ACCESS_DENIED);
        }

        PptStatus pptStatus = resolvePptStatus(script);
        GetScriptDetailRes.PptInfoRes pptInfo = null;
        if (script.getScriptType() == ScriptType.PPT && pptStatus == PptStatus.COMPLETED) {
            List<GetScriptDetailRes.PptSlideRes> slideResList = script.getPptSlides().stream()
                    .map(slide -> GetScriptDetailRes.PptSlideRes.builder()
                            .page(slide.getSlideIndex())
                            .imageUrl(slide.getImageUrl())
                            .build())
                    .toList();

            pptInfo = GetScriptDetailRes.PptInfoRes.builder()
                    .pptUrl(script.getPptUrl())
                    .totalSlides(script.getTotalSlides())
                    .slides(slideResList)
                    .build();
        }

        return GetScriptDetailRes.Response.builder()
                .id(script.getId())
                .title(script.getTitle())
                .content(script.getContent())
                .markedContent(script.getMarkedContent())
                .scriptType(script.getScriptType())
                .pptStatus(pptStatus)
                .pptErrorMessage(script.getPptErrorMessage())
                .createdAt(script.getCreatedAt())
                .pptInfo(pptInfo)
                .build();
    }

    // 발표 대본 삭제 기능 구현
    @Override
    @Transactional
    public DeleteScriptRes.Response deleteScript(Long scriptId, Long userId) {
        Script script = scriptRepository.findByIdWithUser(scriptId)
                .orElseThrow(() -> new CustomException(ScriptErrorCode.SCRIPT_NOT_FOUND));

        if (!script.getUser().getId().equals(userId)) {
            throw new CustomException(ScriptErrorCode.SCRIPT_ACCESS_DENIED);
        }

        // 연관된 연습 기록 먼저 삭제 (외래 키 제약 조건 해결)
        practiceRepository.deleteAllByScriptId(scriptId);

        scriptRepository.delete(script);
        deleteDirectoryQuietly(Paths.get("uploads/ppt/" + scriptId).toAbsolutePath().normalize());
        return DeleteScriptRes.Response.builder()
                .id(scriptId)
                .build();
    }

    // 발표 대본 수정 기능 구현
    @Override
    @Transactional
    public UpdateScriptRes.Response updateScript(Long scriptId, UpdateScriptReq.Request req, Long userId) {
        Script script = scriptRepository.findByIdWithUser(scriptId)
                .orElseThrow(() -> new CustomException(ScriptErrorCode.SCRIPT_NOT_FOUND));

        if (!script.getUser().getId().equals(userId)) {
            throw new CustomException(ScriptErrorCode.SCRIPT_ACCESS_DENIED);
        }

        // 대본 내용이 변경된 경우에만 낭독 기호 대본 새로 생성
        String markedContent = script.getMarkedContent();
        if (!script.getContent().equals(req.getContent())) {
            // 연관된 연습 기록 먼저 삭제 (외래 키 제약 조건 해결)
            practiceRepository.deleteAllByScriptId(scriptId);
            script.getScriptSentences().clear(); // 기존 문장 데이터 삭제
            markedContent = aiAnalysisService.generateMarkedContent(req.getContent());
            if (markedContent == null || markedContent.isBlank()) {
                markedContent = req.getContent();
            }
        }

        script.update(req.getTitle(), req.getContent(), markedContent);
        Script updatedScript = scriptRepository.save(script);

        return UpdateScriptRes.Response.builder()
                .id(updatedScript.getId())
                .title(updatedScript.getTitle())
                .content(updatedScript.getContent())
                .build();
    }

    // [STEP-A] AI 발표 대본 초안 생성 — Python SSE 파싱 후 Flux<String> 중계
    // bodyToFlux(ServerSentEvent.class) → Spring의 SSE 디코더가 "data:" 접두어를 제거하고 값만 추출.
    // 반환 Flux<String>은 Controller의 produces=TEXT_EVENT_STREAM_VALUE가 다시 SSE로 인코딩.
    // 이중 인코딩(data: data: ...) 방지.
    // streamingWebClient 사용 — responseTimeout 5분 (긴 대본 생성 대응).
    @Override
    @SuppressWarnings("unchecked")
    public Flux<String> generateScript(AiGenerateScriptReq.Request req, Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new CustomException(ScriptErrorCode.SCRIPT_USER_NOT_FOUND);
        }

        Map<String, Object> body = new HashMap<>();
        body.put("topic", req.getTopic());
        body.put("time", req.getTime());
        body.put("audienceAge", req.getAudienceAge().name());
        body.put("audienceLevel", req.getAudienceLevel().name());
        body.put("speechType", req.getSpeechType().name());
        body.put("purpose", req.getPurpose());
        body.put("keywords", req.getKeywords());

        return streamingWebClient.post()
                .uri("/scripts/generate")
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                .map(data -> data.replaceFirst("^data:\\s*", ""))
                .onErrorResume(e -> {
                    log.error("AI 발표 대본 생성 스트리밍 실패 - userId: {}", userId, e);
                    return Flux.just("{\"error\":\"AI 대본 생성 중 오류가 발생했습니다.\"}");
                });
    }

    // [STEP-A] AI 발표 대본 최적화 — Python SSE 파싱 후 Flux<String> 중계
    @Override
    @SuppressWarnings("unchecked")
    public Flux<String> updateScript(AiUpdateScriptReq.Request req, Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new CustomException(ScriptErrorCode.SCRIPT_USER_NOT_FOUND);
        }

        Map<String, Object> body = new HashMap<>();
        body.put("topic", req.getTopic());
        body.put("content", req.getContent());
        body.put("time", req.getTime());
        body.put("audienceAge", req.getAudienceAge().name());
        body.put("audienceLevel", req.getAudienceLevel().name());
        body.put("speechType", req.getSpeechType().name());
        body.put("purpose", req.getPurpose());
        body.put("keywords", req.getKeywords());

        return streamingWebClient.post()
                .uri("/scripts/update")
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                .map(data -> data.replaceFirst("^data:\\s*", ""))
                .onErrorResume(e -> {
                    log.error("AI 발표 대본 최적화 스트리밍 실패 - userId: {}", userId, e);
                    return Flux.just("{\"error\":\"AI 대본 최적화 중 오류가 발생했습니다.\"}");
                });
    }

    // PPT 파일 업로드 및 슬라이드 변환 기능 구현
    @Override
    public UploadPptRes.Response uploadPpt(Long scriptId, MultipartFile file, Long userId) {
        Script script = scriptRepository.findByIdWithUser(scriptId)
                .orElseThrow(() -> new CustomException(ScriptErrorCode.SCRIPT_NOT_FOUND));

        if (!script.getUser().getId().equals(userId)) {
            throw new CustomException(ScriptErrorCode.SCRIPT_ACCESS_DENIED);
        }

        if (resolvePptStatus(script) == PptStatus.PROCESSING) {
            throw new CustomException(ScriptErrorCode.SCRIPT_PPT_ALREADY_PROCESSING);
        }

        String previousPptUrl = script.getPptUrl();
        Path uploadDirPath = getPptAttemptDirPath(scriptId);
        boolean processingMarked = false;

        try {
            String sourcePptUrl = savePptFile(scriptId, file, uploadDirPath);
            scriptTxService.markPptProcessing(scriptId, userId);
            processingMarked = true;
            try {
                pptConvertAsyncService.convertPptAsync(scriptId, userId, sourcePptUrl, uploadDirPath, previousPptUrl);
            } catch (TaskRejectedException e) {
                deleteDirectoryQuietly(uploadDirPath);
                scriptTxService.markPptFailed(scriptId, userId, "PPT conversion queue is full.");
                throw new CustomException(ScriptErrorCode.SCRIPT_PPT_CONVERT_FAILED);
            }

            return UploadPptRes.Response.builder()
                    .scriptId(scriptId)
                    .pptStatus(PptStatus.PROCESSING)
                    .message("PPT 변환을 시작했습니다.")
                    .build();
        } catch (CustomException e) {
            deleteDirectoryQuietly(uploadDirPath);
            throw e;
        } catch (Exception e) {
            deleteDirectoryQuietly(uploadDirPath);
            if (processingMarked) {
                scriptTxService.markPptFailed(scriptId, userId, "PPT conversion task could not be started.");
            }
            log.error("PPT 업로드 처리 실패 - scriptId: {}", scriptId, e);
            throw new CustomException(ScriptErrorCode.SCRIPT_PPT_CONVERT_FAILED);
        }
    }

    // PPT 파일 저장 기능 구현
    private String savePptFile(Long scriptId, MultipartFile file, Path uploadDirPath) {
        if (file == null || file.isEmpty()) {
            throw new CustomException(ScriptErrorCode.SCRIPT_PPT_EMPTY_FILE);
        }

        String extension = getPptFileExtension(file.getOriginalFilename());
        if (!extension.equals(".ppt") && !extension.equals(".pptx") && !extension.equals(".pdf")) {
            throw new CustomException(ScriptErrorCode.SCRIPT_PPT_INVALID_EXTENSION);
        }

        try {
            if (!Files.exists(uploadDirPath)) {
                Files.createDirectories(uploadDirPath);
            }

            Path filePath = uploadDirPath.resolve("source" + extension).normalize();
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
            return filePath.toString();
        } catch (Exception e) {
            log.error("PPT 파일 저장 실패 - scriptId: {}", scriptId, e);
            throw new CustomException(ScriptErrorCode.SCRIPT_PPT_UPLOAD_FAILED);
        }
    }

    // PPT 파일 확장자 추출 기능 구현
    private String getPptFileExtension(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "";
        }

        String cleanFileName = Paths.get(fileName).getFileName().toString();
        int dotIndex = cleanFileName.lastIndexOf(".");
        if (dotIndex < 0) {
            return "";
        }

        return cleanFileName.substring(dotIndex).toLowerCase();
    }

    // PPT 변환 출력 디렉터리 경로 생성 기능 구현
    private Path getPptAttemptDirPath(Long scriptId) {
        return Paths.get("uploads/ppt/" + scriptId + "/attempts/" + UUID.randomUUID()).toAbsolutePath().normalize();
    }

    // PPT 변환 상태 확인 기능 구현
    private PptStatus resolvePptStatus(Script script) {
        if (script.getPptStatus() != null) {
            return script.getPptStatus();
        }

        if (script.getScriptType() == ScriptType.PPT && script.getPptUrl() != null) {
            return PptStatus.COMPLETED;
        }

        return PptStatus.NONE;
    }

    // PPT 변환 상태 및 슬라이드 조회 기능 구현
    @Override
    @Transactional(readOnly = true)
    public UploadPptRes.Response getPptStatus(Long scriptId, Long userId) {
        Script script = scriptRepository.findByIdWithUser(scriptId)
                .orElseThrow(() -> new CustomException(ScriptErrorCode.SCRIPT_NOT_FOUND));

        if (!script.getUser().getId().equals(userId)) {
            throw new CustomException(ScriptErrorCode.SCRIPT_ACCESS_DENIED);
        }

        PptStatus status = resolvePptStatus(script);

        if (status != PptStatus.COMPLETED) {
            String message = switch (status) {
                case PROCESSING -> "PPT 변환을 진행하고 있습니다.";
                case FAILED -> script.getPptErrorMessage() != null
                        ? script.getPptErrorMessage()
                        : "PPT 변환에 실패했습니다.";
                default -> "PPT 파일이 없습니다.";
            };
            return UploadPptRes.Response.builder()
                    .scriptId(scriptId)
                    .pptStatus(status)
                    .message(message)
                    .build();
        }

        List<UploadPptRes.PptSlideRes> slides = script.getPptSlides().stream()
                .sorted(java.util.Comparator.comparing(com.speakfit.backend.domain.script.entity.PptSlide::getSlideIndex))
                .map(slide -> UploadPptRes.PptSlideRes.builder()
                        .page(slide.getSlideIndex())
                        .imageUrl(slide.getImageUrl())
                        .build())
                .toList();

        UploadPptRes.PptInfoRes pptInfo = UploadPptRes.PptInfoRes.builder()
                .sourcePptUrl(script.getPptUrl())
                .totalSlides(script.getTotalSlides() != null ? script.getTotalSlides() : slides.size())
                .slides(slides)
                .build();

        return UploadPptRes.Response.builder()
                .scriptId(scriptId)
                .pptStatus(PptStatus.COMPLETED)
                .message("PPT 변환이 완료되었습니다.")
                .pptInfo(pptInfo)
                .build();
    }

    // 디렉터리 삭제 기능 구현
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
                                log.warn("파일 정리 실패 - path: {}", path, e);
                            }
                        });
            }
        } catch (Exception e) {
            log.warn("PPT 업로드 임시 디렉터리 정리 실패 - path: {}", directoryPath, e);
        }
    }

}

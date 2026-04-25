package com.speakfit.backend.domain.script.service;

import com.speakfit.backend.domain.script.dto.req.AddScriptReq;
import com.speakfit.backend.domain.script.dto.req.AiGenerateScriptReq;
import com.speakfit.backend.domain.script.dto.req.AiUpdateScriptReq;
import com.speakfit.backend.domain.script.dto.res.AddScriptRes;
import com.speakfit.backend.domain.script.dto.res.AiGenerateScriptRes;
import com.speakfit.backend.domain.script.dto.res.AiUpdateScriptRes;
import com.speakfit.backend.domain.script.dto.res.DeleteScriptRes;
import com.speakfit.backend.domain.script.dto.res.GetScriptDetailRes;
import com.speakfit.backend.domain.script.dto.res.GetScriptListRes;
import com.speakfit.backend.domain.script.dto.res.UploadPptRes;
import com.speakfit.backend.domain.script.entity.Script;
import com.speakfit.backend.domain.script.enums.ScriptType;
import com.speakfit.backend.domain.script.exception.ScriptErrorCode;
import com.speakfit.backend.domain.script.repository.ScriptRepository;
import com.speakfit.backend.domain.practice.service.AiAnalysisService;
import com.speakfit.backend.domain.user.repository.UserRepository;
import com.speakfit.backend.global.apiPayload.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScriptServiceImpl implements ScriptService {

    private final ScriptRepository scriptRepository;
    private final AiAnalysisService aiAnalysisService;
    private final ScriptTxService scriptTxService;
    private final UserRepository userRepository;
    private final WebClient webClient;

    // 발표 대본 추가 기능 구현
    @Override
    public AddScriptRes.Response addScript(AddScriptReq.Request req, Long userId) {

        if (!userRepository.existsById(userId)) {
            throw new CustomException(ScriptErrorCode.SCRIPT_USER_NOT_FOUND);
        }

        // AI를 통해 낭독 기호 대본 생성
        String markedContent = aiAnalysisService.generateMarkedContent(req.getContent());

        // AI 호출 실패 시 원본 대본 사용
        if (markedContent == null || markedContent.isBlank()) {
            markedContent = req.getContent();
        }

        // 대본 정보 저장
        Script savedScript = scriptTxService.saveScript(req, userId, markedContent);

        // 낭독 기호 대본을 응답 형식으로 변환
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
        Script script = scriptRepository.findById(scriptId)
                .orElseThrow(() -> new CustomException(ScriptErrorCode.SCRIPT_NOT_FOUND));

        if (!script.getUser().getId().equals(userId)) {
            throw new CustomException(ScriptErrorCode.SCRIPT_ACCESS_DENIED);
        }

        GetScriptDetailRes.PptInfoRes pptInfo = null;
        if (script.getScriptType() == ScriptType.PPT) {
            List<GetScriptDetailRes.PptSlideRes> slideResList = script.getPptSlides().stream()
                    .map(slide -> GetScriptDetailRes.PptSlideRes.builder()
                            .page(slide.getSlideIndex())
                            .imageUrl(slide.getImageUrl())
                            .build())
                    .toList();

            pptInfo = GetScriptDetailRes.PptInfoRes.builder()
                    .pptUrl(script.getPptUrl())
                    .slides(slideResList)
                    .build();
        }

        return GetScriptDetailRes.Response.builder()
                .id(script.getId())
                .title(script.getTitle())
                .content(script.getContent())
                .markedContent(script.getMarkedContent())
                .scriptType(script.getScriptType())
                .createdAt(script.getCreatedAt())
                .pptInfo(pptInfo)
                .build();
    }

    // 발표 대본 삭제 기능 구현
    @Override
    @Transactional
    public DeleteScriptRes.Response deleteScript(Long scriptId, Long userId) {
        Script script = scriptRepository.findById(scriptId)
                .orElseThrow(() -> new CustomException(ScriptErrorCode.SCRIPT_NOT_FOUND));

        if (!script.getUser().getId().equals(userId)) {
            throw new CustomException(ScriptErrorCode.SCRIPT_ACCESS_DENIED);
        }

        scriptRepository.delete(script);
        return DeleteScriptRes.Response.builder()
                .id(scriptId)
                .build();
    }

    // AI 발표 대본 초안 생성 기능 구현
    @Override
    public AiGenerateScriptRes.Response generateScript(AiGenerateScriptReq.Request req, Long userId) {
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

        try {
            AiGenerateScriptRes.Response response = webClient.post()
                    .uri("/scripts/generate")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(AiGenerateScriptRes.Response.class)
                    .block();

            if (response == null || response.getGeneratedScript() == null || response.getGeneratedScript().isBlank()) {
                throw new CustomException(ScriptErrorCode.SCRIPT_AI_GENERATE_FAILED);
            }

            return response;
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI 발표 대본 생성 실패 - userId: {}, topic: {}", userId, req.getTopic(), e);
            throw new CustomException(ScriptErrorCode.SCRIPT_AI_GENERATE_FAILED);
        }
    }

    // AI 발표 대본 최적화 기능 구현
    @Override
    public AiUpdateScriptRes.Response updateScript(AiUpdateScriptReq.Request req, Long userId) {
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

        try {
            AiUpdateScriptRes.Response response = webClient.post()
                    .uri("/scripts/update")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(AiUpdateScriptRes.Response.class)
                    .block();

            if (response == null || response.getOptimizedScript() == null || response.getOptimizedScript().isBlank()) {
                throw new CustomException(ScriptErrorCode.SCRIPT_AI_UPDATE_FAILED);
            }

            return response;
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI 발표 대본 최적화 실패 - userId: {}, topic: {}", userId, req.getTopic(), e);
            throw new CustomException(ScriptErrorCode.SCRIPT_AI_UPDATE_FAILED);
        }
    }

    // PPT 파일 업로드 및 슬라이드 변환 기능 구현
    @Override
    public UploadPptRes.Response uploadPpt(Long scriptId, MultipartFile file, Long userId) {
        Script script = scriptRepository.findById(scriptId)
                .orElseThrow(() -> new CustomException(ScriptErrorCode.SCRIPT_NOT_FOUND));

        if (!script.getUser().getId().equals(userId)) {
            throw new CustomException(ScriptErrorCode.SCRIPT_ACCESS_DENIED);
        }

        String sourcePptUrl = savePptFile(scriptId, file);

        return UploadPptRes.Response.builder()
                .scriptId(scriptId)
                .pptInfo(UploadPptRes.PptInfoRes.builder()
                        .sourcePptUrl(sourcePptUrl)
                        .totalSlides(0)
                        .slides(List.of())
                        .build())
                .build();
    }

    // PPT 원본 파일 저장 기능 구현
    private String savePptFile(Long scriptId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CustomException(ScriptErrorCode.SCRIPT_PPT_EMPTY_FILE);
        }

        String extension = getPptFileExtension(file.getOriginalFilename());
        if (!extension.equals(".ppt") && !extension.equals(".pptx")) {
            throw new CustomException(ScriptErrorCode.SCRIPT_PPT_INVALID_EXTENSION);
        }

        try {
            Path uploadDirPath = Paths.get("uploads/ppt/" + scriptId).toAbsolutePath().normalize();
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
}

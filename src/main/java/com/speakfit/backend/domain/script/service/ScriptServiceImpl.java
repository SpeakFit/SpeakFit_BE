package com.speakfit.backend.domain.script.service;

import com.speakfit.backend.domain.script.dto.req.AddScriptReq;
import com.speakfit.backend.domain.script.dto.res.AddScriptRes;
import com.speakfit.backend.domain.script.dto.res.DeleteScriptRes;
import com.speakfit.backend.domain.script.dto.res.GetScriptDetailRes;
import com.speakfit.backend.domain.script.dto.res.GetScriptListRes;
import com.speakfit.backend.domain.script.entity.Script;
import com.speakfit.backend.domain.script.enums.ScriptType;
import com.speakfit.backend.domain.script.exception.ScriptErrorCode;
import com.speakfit.backend.domain.script.repository.ScriptRepository;
import com.speakfit.backend.domain.user.entity.User;
import com.speakfit.backend.domain.user.repository.UserRepository;
import com.speakfit.backend.global.apiPayload.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScriptServiceImpl implements ScriptService {

    private final ScriptRepository scriptRepository;
    private final UserRepository userRepository;

    // 발표 대본 추가 기능 서비스 구현 구현
    @Override
    @Transactional
    public AddScriptRes.Response addScript(AddScriptReq.Request req, Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ScriptErrorCode.SCRIPT_USER_NOT_FOUND));

        // 1. dto -> entity 변환 (기본 타입을 TEXT로 설정)
        Script script = Script.builder()
                .title(req.getTitle())
                .content(req.getContent())
                .scriptType(ScriptType.TEXT) // 최신화된 엔티티 필드 반영
                .user(user)
                .build();

        // 2. db 저장
        Script savedScript = scriptRepository.save(script);

        // 3. entity -> res dto 변환 및 반환
        return AddScriptRes.Response.builder()
                .id(savedScript.getId())
                .title(savedScript.getTitle())
                .content(savedScript.getContent())
                .createdAt(savedScript.getCreatedAt())
                .build();
    }

    // 발표 대본 목록 조회 기능 서비스 구현 구현
    @Override
    public List<GetScriptListRes.Response> getScripts(Long userId) {


        // 1. Repository에서 유저의 대본 리스트 가져오기
        List<Script> scripts = scriptRepository.findAllByUserId(userId);

        // 2. Entity리스트 -> dto리스트 변환 및 반환
        return scripts.stream()
                .map(script -> GetScriptListRes.Response.builder()
                        .id(script.getId())
                        .title(script.getTitle())
                        .content(script.getContent())
                        .scriptType(script.getScriptType()) // 추가
                        .createdAt(script.getCreatedAt())
                        .build())
                .toList();
        }

    // 발표 대본 상세 조회 기능 구현
    @Override
    public GetScriptDetailRes.Response getScript(Long scriptId, Long userId) {
        // 1. DB에서 대본 찾기
        Script script = scriptRepository.findById(scriptId)
                .orElseThrow(() -> new CustomException(ScriptErrorCode.SCRIPT_NOT_FOUND));

        // 2. 사용자 권한 체크 로직
        if (!script.getUser().getId().equals(userId)) {
            throw new CustomException(ScriptErrorCode.SCRIPT_ACCESS_DENIED);
        }

        // 3. PPT 정보 구성 (PPT 타입일 때만)
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

        // 4. Entity -> DTO 변환 및 반환
        return GetScriptDetailRes.Response.builder()
                .id(script.getId())
                .title(script.getTitle())
                .content(script.getContent())
                .markedContent(script.getMarkedContent())
                .scriptType(script.getScriptType())
                .createdAt(script.getCreatedAt())
                .pptInfo(pptInfo) // PPT가 없으면 null로 전달
                .build();
    }

    // 발표 대본 삭제 기능 서비스 구현 구현
    @Override
    @Transactional
    public DeleteScriptRes.Response deleteScript(Long scriptId, Long userId) {
        // 1. DB에서 대본 찾기
        Script script = scriptRepository.findById(scriptId)
                .orElseThrow(() -> new CustomException(ScriptErrorCode.SCRIPT_NOT_FOUND));

        // 2. 사용자 권한 체크
        if (!script.getUser().getId().equals(userId)) {
            throw new CustomException(ScriptErrorCode.SCRIPT_ACCESS_DENIED);
        }

        // 3. 삭제 처리
        scriptRepository.delete(script);

        // 4. 삭제된 ID 반환
        return DeleteScriptRes.Response.builder()
                .id(scriptId)
                .build();
    }
}

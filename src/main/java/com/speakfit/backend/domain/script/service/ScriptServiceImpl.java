package com.speakfit.backend.domain.script.service;

import com.speakfit.backend.domain.script.dto.req.AddScriptReq;
import com.speakfit.backend.domain.script.dto.res.AddScriptRes;
import com.speakfit.backend.domain.script.dto.res.DeleteScriptRes;
import com.speakfit.backend.domain.script.dto.res.GetScriptDetailRes;
import com.speakfit.backend.domain.script.dto.res.GetScriptListRes;
import com.speakfit.backend.domain.script.entity.Script;
import com.speakfit.backend.domain.script.exception.ScriptErrorCode;
import com.speakfit.backend.domain.script.repository.ScriptRepository;
import com.speakfit.backend.domain.user.entity.User;
import com.speakfit.backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.speakfit.backend.global.apiPayload.exception.CustomException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScriptServiceImpl implements ScriptService {

    private final ScriptRepository scriptRepository;
    private final UserRepository userRepository;

    // 발표 대본 추가 기능 서비스 구현
    @Override
    @Transactional
    public AddScriptRes addScript(AddScriptReq.Request req) {

        //--[임시 코드] jwt 구현 전이라 임의로 1번 유저를 가져옴--
        Long tempUserId = 1L;
        User user = userRepository.findById(tempUserId)
                .orElseThrow(() -> new CustomException(ScriptErrorCode.SCRIPT_USER_NOT_FOUND));

        // 1.dto->entity 변환
        Script script = Script.builder()
                .title(req.getTitle())
                .content(req.getContent())
                .user(user)
                .build();

        // 2.db 저장
        Script savedScript = scriptRepository.save(script);

        // 3. entity->res dto 변환 및 반환
        return AddScriptRes.builder()
                .id(savedScript.getId())
                .title(savedScript.getTitle())
                .content(savedScript.getContent())
                .createdAt(savedScript.getCreatedAt())
                .build();
    }

    //발표 대본 목록 조회 기능 서비스 구현
    @Override
    public List<GetScriptListRes> getScripts() {

        // [임시] 1번 유저의 대본목록 조회
        Long tempUserId = 1L;

        // 1. Repository에서 유저의 대본 리스트 가져오기
        List<Script> scripts = scriptRepository.findAllByUserId(tempUserId);

        // 2. Entity리스트 -> dto리스트 변환 및 반환
        return scripts.stream()
                .map(script -> GetScriptListRes.builder()
                        .id(script.getId())
                        .title(script.getTitle())
                        .content(script.getContent())
                        .createdAt(script.getCreatedAt())
                        .build())
                .toList();
    }

    // 발표 대본 상세 조회 기능 서비스 구현
    @Override
    public GetScriptDetailRes getScript(Long scriptId) {
        // 1. DB에서 대본 찾기
        Script script=scriptRepository.findById(scriptId)
                .orElseThrow(()-> new CustomException(ScriptErrorCode.SCRIPT_NOT_FOUND));
        // 2. 사용자 권한 체크 로직
        Long currentUserId=1L;// jwt구현전이라 임시로 1번사용자의 대본만 확인하도록 설정함.
        if(!script.getUser().getId().equals(currentUserId)) {
            throw new CustomException(ScriptErrorCode.SCRIPT_ACCESS_DENIED);
            }


        // 3. Entity -> DTO 변환 및 반환
        return GetScriptDetailRes.builder()
                .id(script.getId())
                .title(script.getTitle())
                .content(script.getContent())
                .createdAt(script.getCreatedAt())
                .build();
    }

    // 발표 대본 삭제 기능 서비스 구현
    @Override
    @Transactional
    public DeleteScriptRes deleteScript(Long scriptId) {
        // 1. DB에서 대본 찾기
        Script script = scriptRepository.findById(scriptId)
                .orElseThrow(()->new CustomException(ScriptErrorCode.SCRIPT_NOT_FOUND));

        // 2. 사용자 권한 체크 (JWT 구현 전이라 임시 1번 유저)
        Long currentUserId =1L;
        if(!script.getUser().getId().equals(currentUserId)) {
            throw new CustomException(ScriptErrorCode.SCRIPT_ACCESS_DENIED);
        }

        // 3. 삭제 처리
        scriptRepository.delete(script);

        // 4. 삭제된 ID 반환
        return DeleteScriptRes.builder()
                .id(scriptId)
                .build();
    }
}

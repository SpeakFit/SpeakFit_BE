package com.speakfit.backend.domain.script.service;

import com.speakfit.backend.domain.script.dto.req.AddScriptReq;
import com.speakfit.backend.domain.script.dto.res.AddScriptRes;
import com.speakfit.backend.domain.script.entity.Script;
import com.speakfit.backend.domain.script.repository.ScriptRepository;
import com.speakfit.backend.domain.user.entity.User;
import com.speakfit.backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ScriptServiceImpl implements ScriptService {

    private final ScriptRepository scriptRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public AddScriptRes addScript(AddScriptReq.Request req){

        //--[임시 코드] jwt 구현 전이라 임의로 1번 유저를 가져옴--
        Long tempUserId = 1L;
        User user = userRepository.findById(tempUserId)
                .orElseThrow(() -> new RuntimeException("임시 유저(ID:1)가 DB에 없습니다."));

        // 1.dto->entity 변환
        Script script=Script.builder()
                .title(req.getTitle())
                .content(req.getContent())
                .user(user)
                .build();

        // 2.db 저장
        Script savedScript=scriptRepository.save(script);

        // 3. entity->res dto 변환 및 반환
        return AddScriptRes.builder()
                .id(savedScript.getId())
                .title(savedScript.getTitle())
                .content(savedScript.getContent())
                .createdAt(savedScript.getCreatedAt())
                .build();
    }
}

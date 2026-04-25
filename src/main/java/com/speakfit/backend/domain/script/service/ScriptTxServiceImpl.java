package com.speakfit.backend.domain.script.service;

import com.speakfit.backend.domain.script.dto.req.AddScriptReq;
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

@Service
@RequiredArgsConstructor
public class ScriptTxServiceImpl implements ScriptTxService {

    private final ScriptRepository scriptRepository;
    private final UserRepository userRepository;

    // 발표 대본 저장 트랜잭션 기능 구현
    @Override
    @Transactional
    public Script saveScript(AddScriptReq.Request req, Long userId, String markedContent) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ScriptErrorCode.SCRIPT_USER_NOT_FOUND));

        Script script = Script.builder()
                .title(req.getTitle())
                .content(req.getContent())
                .markedContent(markedContent)
                .scriptType(ScriptType.TEXT)
                .user(user)
                .build();
        return scriptRepository.save(script);
    }
}

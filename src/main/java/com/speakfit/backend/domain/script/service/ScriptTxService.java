package com.speakfit.backend.domain.script.service;

import com.speakfit.backend.domain.script.dto.req.AddScriptReq;
import com.speakfit.backend.domain.script.entity.Script;

public interface ScriptTxService {

    // 발표 대본 저장 트랜잭션 기능 정의
    Script saveScript(AddScriptReq.Request req, Long userId, String markedContent);
}

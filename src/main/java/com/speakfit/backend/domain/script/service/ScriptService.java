package com.speakfit.backend.domain.script.service;

import com.speakfit.backend.domain.script.dto.req.AddScriptReq;
import com.speakfit.backend.domain.script.dto.res.AddScriptRes;
public interface ScriptService {
    // 대본 추가 메서드 정의
    AddScriptRes addScript(AddScriptReq.Request req);
}
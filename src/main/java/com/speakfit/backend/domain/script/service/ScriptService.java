package com.speakfit.backend.domain.script.service;

import com.speakfit.backend.domain.script.dto.req.AddScriptReq;
import com.speakfit.backend.domain.script.dto.res.AddScriptRes;
import com.speakfit.backend.domain.script.dto.res.GetScriptDetailRes;
import com.speakfit.backend.domain.script.dto.res.GetScriptListRes;

import java.util.List;

public interface ScriptService {
    // 발표 대본 추가 메서드 정의
    AddScriptRes addScript(AddScriptReq.Request req);

    // 발표 대본 목록 조회 메서드 정의
    List<GetScriptListRes> getScripts();

    //발표 대본 상세 조회 메서드 정의
    GetScriptDetailRes getScript(Long scriptId);
}
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

import java.util.List;

public interface ScriptService {
    // 발표 대본 추가 메서드 정의
    AddScriptRes.Response addScript(AddScriptReq.Request req, Long userId);

    // 발표 대본 목록 조회 메서드 정의
    List<GetScriptListRes.Response> getScripts(Long userId);

    // 발표 대본 상세 조회 메서드 정의
    GetScriptDetailRes.Response getScript(Long scriptId, Long userId);

    // 발표 대본 삭제 메서드 정의
    DeleteScriptRes.Response deleteScript(Long scriptId, Long userId);

    // AI 발표 대본 초안 생성 메서드 정의
    AiGenerateScriptRes.Response generateScript(AiGenerateScriptReq.Request req, Long userId);

    // AI 발표 대본 최적화 메서드 정의
    AiUpdateScriptRes.Response updateScript(AiUpdateScriptReq.Request req, Long userId);
}

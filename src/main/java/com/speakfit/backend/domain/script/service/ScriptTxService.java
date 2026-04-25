package com.speakfit.backend.domain.script.service;

import com.speakfit.backend.domain.script.dto.req.AddScriptReq;
import com.speakfit.backend.domain.script.dto.res.UploadPptRes;
import com.speakfit.backend.domain.script.entity.Script;

import java.util.List;

public interface ScriptTxService {

    // 발표 대본 저장 트랜잭션 기능 정의
    Script saveScript(AddScriptReq.Request req, Long userId, String markedContent);

    // PPT 정보 저장 트랜잭션 기능 정의
    void savePptInfo(Long scriptId, Long userId, String sourcePptUrl, Integer totalSlides, List<UploadPptRes.PptSlideRes> slides);
}

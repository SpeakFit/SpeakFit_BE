package com.speakfit.backend.domain.script.service;

import com.speakfit.backend.domain.script.dto.req.AddScriptReq;
import com.speakfit.backend.domain.script.dto.res.UploadPptRes;
import com.speakfit.backend.domain.script.entity.Script;

import java.util.List;

public interface ScriptTxService {

    Script saveScript(AddScriptReq.Request req, Long userId, String markedContent);

    void markPptProcessing(Long scriptId, Long userId);

    void savePptSuccess(Long scriptId, Long userId, String sourcePptUrl, Integer totalSlides, List<UploadPptRes.PptSlideRes> slides);

    void markPptFailed(Long scriptId, Long userId, String errorMessage);
}

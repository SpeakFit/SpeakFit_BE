package com.speakfit.backend.domain.script.service;

import com.speakfit.backend.domain.script.dto.req.AddScriptReq;
import com.speakfit.backend.domain.script.dto.res.UploadPptRes;
import com.speakfit.backend.domain.script.entity.PptSlide;
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
public class ScriptTxServiceImpl implements ScriptTxService {

    private final ScriptRepository scriptRepository;
    private final UserRepository userRepository;

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

    @Override
    @Transactional
    public void markPptProcessing(Long scriptId, Long userId) {
        Script script = findOwnedScript(scriptId, userId);
        script.markPptProcessing();
    }

    @Override
    @Transactional
    public void savePptSuccess(Long scriptId, Long userId, String sourcePptUrl, Integer totalSlides, List<UploadPptRes.PptSlideRes> slides) {
        Script script = findOwnedScript(scriptId, userId);
        script.updatePptInfo(sourcePptUrl, totalSlides);
        slides.forEach(slide -> script.addPptSlide(PptSlide.builder()
                .slideIndex(slide.getPage())
                .imageUrl(slide.getImageUrl())
                .build()));
    }

    @Override
    @Transactional
    public void markPptFailed(Long scriptId, Long userId, String errorMessage) {
        Script script = findOwnedScript(scriptId, userId);
        script.markPptFailed(errorMessage);
    }

    private Script findOwnedScript(Long scriptId, Long userId) {
        Script script = scriptRepository.findByIdWithUser(scriptId)
                .orElseThrow(() -> new CustomException(ScriptErrorCode.SCRIPT_NOT_FOUND));

        if (!script.getUser().getId().equals(userId)) {
            throw new CustomException(ScriptErrorCode.SCRIPT_ACCESS_DENIED);
        }

        return script;
    }
}

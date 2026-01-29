package com.speakfit.backend.domain.practice.service;

import com.speakfit.backend.domain.practice.dto.req.StartPracticeReq;
import com.speakfit.backend.domain.practice.dto.res.StartPracticeRes;
import com.speakfit.backend.domain.practice.entity.PracticeRecord;
import com.speakfit.backend.domain.practice.enums.PracticeStatus;
import com.speakfit.backend.domain.practice.repository.PracticeRepository;
import com.speakfit.backend.domain.script.entity.Script;
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
@Transactional(readOnly = true)
public class PracticeServiceImpl implements PracticeService {

    private final PracticeRepository practiceRepository;
    private final ScriptRepository scriptRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public StartPracticeRes startPractice(StartPracticeReq.Request req) {

        // 1. 임시 사용자 조회 (JWT 적용 전 1번 유저 고정)
        Long tempUserId = 1L;
        User user = userRepository.findById(tempUserId)
                .orElseThrow(() -> new CustomException(ScriptErrorCode.SCRIPT_USER_NOT_FOUND));

        // 2. 발표 대본 조회
        Script script = scriptRepository.findById(req.getScriptId())
                .orElseThrow(() -> new CustomException(ScriptErrorCode.SCRIPT_NOT_FOUND));

        // 3. 사용자 권한 체크
        if (!script.getUser().getId().equals(tempUserId)) {
            throw new CustomException(ScriptErrorCode.SCRIPT_ACCESS_DENIED);
        }

        // 4. 연습 기록 생성
        PracticeRecord practiceRecord = PracticeRecord.builder()
                .user(user)
                .script(script)
                .status(PracticeStatus.RECORDING)
                .time(0.0)
                .audioUrl(null)
                .build();

        // 5. DB에 저장
        PracticeRecord savedRecord = practiceRepository.save(practiceRecord);

        // 6. Entity -> DTO 변환 및 반환
        return StartPracticeRes.builder()
                .practiceId(savedRecord.getId())
                .status(savedRecord.getStatus().toString())
                .build();

    }
}

package com.speakfit.backend.domain.style.service;

import com.speakfit.backend.domain.style.dto.res.SpeechStylesGetRes;
import com.speakfit.backend.domain.style.exception.SpeechStyleErrorCode;
import com.speakfit.backend.domain.style.repository.SpeechStyleRepository;
import com.speakfit.backend.domain.user.entity.User;
import com.speakfit.backend.domain.user.enums.Gender;
import com.speakfit.backend.domain.user.repository.UserRepository;
import com.speakfit.backend.domain.script.exception.ScriptErrorCode;
import com.speakfit.backend.global.apiPayload.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpeechStyleQueryServiceImpl implements SpeechStyleQueryService {

    private final SpeechStyleRepository speechStyleRepository;
    private final UserRepository userRepository;

    // 스피치 스타일 조회 (사용자 성별에 맞는 샘플 오디오 URL 반환)
    @Override
    public SpeechStylesGetRes getStyles(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ScriptErrorCode.SCRIPT_USER_NOT_FOUND));

        Gender gender = user.getGender();

        var styles = speechStyleRepository.findAllByOrderBySortOrderAscIdAsc();

        if (styles.isEmpty()) {
            throw new CustomException(SpeechStyleErrorCode.STYLES_EMPTY);
        }

        var items = styles.stream()
                .map(s -> SpeechStylesGetRes.StyleItem.builder()
                        .styleId(s.getId())
                        .styleType(s.getStyleType())
                        .displayName(s.getDisplayName())
                        .description(s.getDescription())
                        .sampleAudioUrl(
                                gender == Gender.FEMALE
                                        ? s.getSampleAudioUrlFemale()
                                        : s.getSampleAudioUrlMale()
                        )
                        .build())
                .toList();

        return SpeechStylesGetRes.builder()
                .styles(items)
                .build();
    }
}

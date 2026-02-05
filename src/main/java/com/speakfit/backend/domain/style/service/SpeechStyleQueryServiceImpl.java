package com.speakfit.backend.domain.style.service;

import com.speakfit.backend.domain.style.dto.res.SpeechStylesGetRes;
import com.speakfit.backend.domain.style.exception.SpeechStyleErrorCode;
import com.speakfit.backend.domain.style.repository.SpeechStyleRepository;
import com.speakfit.backend.global.apiPayload.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpeechStyleQueryServiceImpl implements SpeechStyleQueryService {

    private final SpeechStyleRepository speechStyleRepository;

    // 스피치 스타일 조회
    @Override
    public SpeechStylesGetRes getStyles(){
        var styles = speechStyleRepository.findAllByOrderByIdAsc();

        if (styles.isEmpty()){
            throw new CustomException(SpeechStyleErrorCode.STYLES_EMPTY);
        }

        var items = styles.stream()
                .map(s->SpeechStylesGetRes.StyleItem.builder()
                        .styleId(s.getId())
                        .description(s.getDescription())
                        .sampleAudioUrl(s.getSampleAudioUrl())
                        .build())
                .toList();

        return SpeechStylesGetRes.builder()
                .styles(items)
                .build();
    }
}

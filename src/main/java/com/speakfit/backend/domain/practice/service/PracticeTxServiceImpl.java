package com.speakfit.backend.domain.practice.service;

import com.speakfit.backend.domain.practice.entity.PracticeRecord;
import com.speakfit.backend.domain.practice.enums.Status;
import com.speakfit.backend.domain.practice.exception.PracticeErrorCode;
import com.speakfit.backend.domain.practice.repository.PracticeRepository;
import com.speakfit.backend.domain.style.entity.SpeechStyle;
import com.speakfit.backend.domain.style.repository.SpeechStyleRepository;
import com.speakfit.backend.global.apiPayload.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PracticeTxServiceImpl implements PracticeTxService {

    private final PracticeRepository practiceRepository;
    private final SpeechStyleRepository speechStyleRepository;

    @Override
    @Transactional
    public void updateStyle(Long practiceId, Long styleId) {
        PracticeRecord record = practiceRepository.findById(practiceId)
                .orElseThrow(() -> new CustomException(PracticeErrorCode.PRACTICE_NOT_FOUND));

        SpeechStyle style = speechStyleRepository.findById(styleId)
                .orElseThrow(() -> new CustomException(PracticeErrorCode.PRACTICE_NOT_FOUND));

        record.selectStyle(style);
        practiceRepository.save(record);
    }

    @Override
    @Transactional
    public void saveStopPracticeResults(Long practiceId, String audioUrl, Double time) {
        PracticeRecord practiceRecord = practiceRepository.findById(practiceId)
                .orElseThrow(() -> new CustomException(PracticeErrorCode.PRACTICE_NOT_FOUND));

        practiceRecord.stopRecording(audioUrl, time);
        practiceRecord.updateStatus(Status.ANALYZING);
        practiceRepository.save(practiceRecord);
    }
}

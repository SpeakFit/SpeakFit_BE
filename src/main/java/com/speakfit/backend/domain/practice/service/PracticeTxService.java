package com.speakfit.backend.domain.practice.service;

import com.speakfit.backend.domain.practice.enums.Status;

public interface PracticeTxService {
    void updateStyleAndMarkedContent(Long practiceId, Long styleId, String markedContent);
    void saveStopPracticeResults(Long practiceId, String audioUrl, Double time);
}

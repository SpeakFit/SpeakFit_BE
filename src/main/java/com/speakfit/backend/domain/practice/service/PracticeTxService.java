package com.speakfit.backend.domain.practice.service;

import com.speakfit.backend.domain.practice.enums.Status;

public interface PracticeTxService {
    void updateStyle(Long practiceId, Long styleId);
    void saveStopPracticeResults(Long practiceId, String audioUrl, Double time);
}

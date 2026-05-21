package com.speakfit.backend.domain.style.entity;

import com.speakfit.backend.domain.style.enums.StyleType;
import com.speakfit.backend.domain.user.enums.Dialect;
import com.speakfit.backend.domain.user.enums.Gender;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SpeechStyleMatrixTest {

    @Test
    void validateRatiosAllowsPositiveRatios() {
        SpeechStyleMatrix matrix = createMatrix(0.8, 0.32);

        assertDoesNotThrow(matrix::validateRatios);
    }

    @Test
    void validateRatiosRejectsZeroPitchRatio() {
        SpeechStyleMatrix matrix = createMatrix(0.0, 0.32);

        assertThrows(IllegalStateException.class, matrix::validateRatios);
    }

    @Test
    void validateRatiosRejectsNegativeWpmRatio() {
        SpeechStyleMatrix matrix = createMatrix(0.8, -0.32);

        assertThrows(IllegalStateException.class, matrix::validateRatios);
    }

    private SpeechStyleMatrix createMatrix(Double pitchRatio, Double wpmRatio) {
        return SpeechStyleMatrix.builder()
                .dialect(Dialect.STANDARD)
                .gender(Gender.MALE)
                .styleType(StyleType.CALM_LOW_TONE)
                .pitchRatio(pitchRatio)
                .wpmRatio(wpmRatio)
                .build();
    }
}

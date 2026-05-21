package com.speakfit.backend.domain.style.repository;

import com.speakfit.backend.domain.style.entity.SpeechStyleMatrix;
import com.speakfit.backend.domain.style.enums.StyleType;
import com.speakfit.backend.domain.user.enums.Dialect;
import com.speakfit.backend.domain.user.enums.Gender;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SpeechStyleMatrixRepository extends JpaRepository<SpeechStyleMatrix, Long> {

    List<SpeechStyleMatrix> findAllByDialectAndGenderOrderByIdAsc(Dialect dialect, Gender gender);

    Optional<SpeechStyleMatrix> findByDialectAndGenderAndStyleType(
            Dialect dialect,
            Gender gender,
            StyleType styleType
    );
}

package com.speakfit.backend.domain.feedback.repository;

import com.speakfit.backend.domain.feedback.entity.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
}

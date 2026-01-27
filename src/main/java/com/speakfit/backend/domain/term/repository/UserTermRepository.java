package com.speakfit.backend.domain.term.repository;

import com.speakfit.backend.domain.term.entity.mapping.UserTerm;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserTermRepository extends JpaRepository<UserTerm, Long> {

    // 특정 유저의 약관 동의 목록 조회
    List<UserTerm> findByUserId(Long userId);

    // 특정 유저가 특정 약관에 동의했는지 여부 확인
    boolean existsByUserIdAndTermIdAndAgreedTrue(Long userId, Long termId);
}

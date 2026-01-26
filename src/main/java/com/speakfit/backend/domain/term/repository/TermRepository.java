package com.speakfit.backend.domain.term.repository;

import com.speakfit.backend.domain.term.entity.Term;
import com.speakfit.backend.domain.term.enums.TermType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TermRepository extends JpaRepository<Term, Long> {

    // enum(terms_type)로 단건 조회: 약관 상세 조회 / 내부 검증용
    Optional<Term> findByTermsType(TermType termsType);

    // 필수 약관만 조회: 회원가입 필수 동의 검증에 유용
    List<Term> findByRequiredTrue();
}

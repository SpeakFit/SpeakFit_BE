package com.speakfit.backend.domain.auth.service;

import com.speakfit.backend.domain.auth.dto.req.SignUpReq;
import com.speakfit.backend.domain.auth.dto.res.SignUpRes;
import com.speakfit.backend.domain.auth.exception.AuthErrorCode;
import com.speakfit.backend.domain.style.entity.SpeechStyle;
import com.speakfit.backend.domain.style.repository.SpeechStyleRepository;
import com.speakfit.backend.domain.term.entity.Term;
import com.speakfit.backend.domain.term.entity.mapping.UserTerm;
import com.speakfit.backend.domain.term.enums.TermType;
import com.speakfit.backend.domain.term.repository.TermRepository;
import com.speakfit.backend.domain.term.repository.UserTermRepository;
import com.speakfit.backend.domain.user.entity.User;
import com.speakfit.backend.domain.user.repository.UserRepository;
import com.speakfit.backend.global.apiPayload.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final SpeechStyleRepository speechStyleRepository;
    private final TermRepository termRepository;
    private final UserTermRepository userTermRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public SignUpRes signUp(SignUpReq.Request req) {

        // 1️⃣ 스타일 조회
        SpeechStyle style = speechStyleRepository.findById(req.getStyleId())
                .orElseThrow(() -> new CustomException(AuthErrorCode.STYLE_NOT_FOUND));

        // 2️⃣ User 생성
        User user = User.builder()
                .usersId(req.getUsersId())
                .password(passwordEncoder.encode(req.getPassword()))
                .name(req.getName())
                .phoneNum(req.getPhoneNum())
                .nickname(req.getNickname())
                .birth(LocalDate.parse(req.getBirth()))
                .gender(req.getGender())
                .style(style)
                .build();

        User savedUser;
        try {
            savedUser = userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            String msg = e.getMostSpecificCause().getMessage();

            if (msg.contains("uk_user_users_id")) {
                throw new CustomException(AuthErrorCode.DUPLICATE_USERS_ID);
            }
            if (msg.contains("uk_user_nickname")) {
                throw new CustomException(AuthErrorCode.DUPLICATE_NICKNAME);
            }
            if (msg.contains("uk_user_phone")) {
                throw new CustomException(AuthErrorCode.DUPLICATE_PHONE);
            }

            throw e; // 예상 못 한 DB 에러는 그대로
        }

        // 3️⃣ 약관 동의 처리
        Map<TermType, Boolean> agreedMap =
                req.getTerms().stream()
                        .collect(Collectors.toMap(
                                SignUpReq.TermAgreement::getTermType,
                                SignUpReq.TermAgreement::getAgreed,
                                (a, b) -> b
                        ));

        List<Term> allTerms = termRepository.findAll();

        for (Term term : allTerms) {
            if (term.isRequired()) {
                Boolean agreed = agreedMap.get(term.getTermType());
                if (agreed == null || !agreed) {
                    throw new CustomException(AuthErrorCode.REQUIRED_TERM_NOT_AGREED);
                }
            }
        }

        List<UserTerm> userTerms = allTerms.stream()
                .map(term -> UserTerm.builder()
                        .user(savedUser)
                        .term(term)
                        .agreed(Boolean.TRUE.equals(agreedMap.get(term.getTermType())))
                        .build()
                )
                .toList();

        userTermRepository.saveAll(userTerms);

        // 4️⃣ 응답
        return SignUpRes.builder()
                .userId(savedUser.getId())
                .usersId(savedUser.getUsersId())
                .nickname(savedUser.getNickname())
                .build();
    }
}

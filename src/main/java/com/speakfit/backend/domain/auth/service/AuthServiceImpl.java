package com.speakfit.backend.domain.auth.service;

import com.speakfit.backend.domain.auth.dto.req.LoginReq;
import com.speakfit.backend.domain.auth.dto.req.SignUpReq;
import com.speakfit.backend.domain.auth.dto.res.LoginRes;
import com.speakfit.backend.domain.auth.dto.res.SignUpRes;
import com.speakfit.backend.domain.auth.entity.RefreshToken;
import com.speakfit.backend.domain.auth.exception.AuthErrorCode;
import com.speakfit.backend.domain.auth.repository.RefreshTokenRepository;
import com.speakfit.backend.domain.term.entity.Term;
import com.speakfit.backend.domain.term.entity.mapping.UserTerm;
import com.speakfit.backend.domain.term.repository.TermRepository;
import com.speakfit.backend.domain.term.repository.UserTermRepository;
import com.speakfit.backend.domain.user.entity.User;
import com.speakfit.backend.domain.user.repository.UserRepository;
import com.speakfit.backend.global.apiPayload.exception.CustomException;
import com.speakfit.backend.global.infra.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final TermRepository termRepository;
    private final UserTermRepository userTermRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    /** 회원가입 **/
    @Override
    public SignUpRes signUp(SignUpReq.Request req) {

        // 1. User 생성
        User user = User.builder()
                .email(req.getEmail())
                .password(passwordEncoder.encode(req.getPassword()))
                .nickname(req.getNickname())
                .birthday(req.getBirthday())
                .gender(req.getGender())
                .dialect(req.getDialect())
                .build();

        User savedUser;
        try {
            savedUser = userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            String msg = e.getMostSpecificCause().getMessage();

            if (msg.contains("uk_user_email")) {
                throw new CustomException(AuthErrorCode.DUPLICATE_EMAIL);
            }
            if (msg.contains("uk_user_nickname")) {
                throw new CustomException(AuthErrorCode.DUPLICATE_NICKNAME);
            }

            throw e; // 예상 못 한 DB 에러는 그대로
        }

        // 2. 약관 동의 처리
        Map<Long, Boolean> agreedMap =
                req.getTerms().stream()
                        .collect(Collectors.toMap(
                                SignUpReq.TermAgreement::getTermId,
                                SignUpReq.TermAgreement::getAgreed,
                                (a, b) -> b
                        ));

        List<Term> allTerms = termRepository.findAll();

        for (Term term : allTerms) {
            if (term.isRequired()) {
                Boolean agreed = agreedMap.get(term.getId());
                if (agreed == null || !agreed) {
                    throw new CustomException(AuthErrorCode.REQUIRED_TERM_NOT_AGREED);
                }
            }
        }

        List<UserTerm> userTerms = allTerms.stream()
                .map(term -> UserTerm.builder()
                        .user(savedUser)
                        .term(term)
                        .agreed(Boolean.TRUE.equals(agreedMap.get(term.getId())))
                        .build()
                )
                .toList();

        userTermRepository.saveAll(userTerms);

        // 3. 응답
        return SignUpRes.builder()
                .userId(savedUser.getId())
                .email(savedUser.getEmail())
                .nickname(savedUser.getNickname())
                .build();
    }
    /** 로그인 **/
    @Override
    public LoginRes login(LoginReq.Request req){

        User user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new CustomException(AuthErrorCode.LOGIN_FAILED));

        if(!passwordEncoder.matches(req.getPassword(), user.getPassword())){
            throw new CustomException(AuthErrorCode.LOGIN_FAILED);
        }

        // 토큰 생성
        String accessToken = jwtProvider.createAccessToken(user.getId(), user.getEmail());
        String refreshToken = jwtProvider.createRefreshToken(user.getId());
        Instant refreshExpiresAt = jwtProvider.getRefreshTokenExpiresAt();

        // refreshToken DB 저장
        RefreshToken rt = refreshTokenRepository.findByUser(user)
                .orElseGet(() -> RefreshToken.builder()
                        .user(user)
                        .token(refreshToken)
                        .expiresAt(refreshExpiresAt)
                        .build());

        rt.updateToken(refreshToken, refreshExpiresAt);
        refreshTokenRepository.save(rt);

        return LoginRes.builder()
                .accessToken(accessToken)
                .user(LoginRes.UserInfo.from(user))
                .refreshToken(refreshToken)
                .refreshTokenMaxAgeSeconds(
                        Duration.between(Instant.now(), refreshExpiresAt).getSeconds()
                )
                .build();
    }
}

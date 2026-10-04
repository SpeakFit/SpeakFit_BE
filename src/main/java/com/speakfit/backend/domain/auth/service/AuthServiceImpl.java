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

        // 1. 이메일·닉네임 중복 사전 검사 (DB constraint 파싱 방식 대신 명시적 검사)
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new CustomException(AuthErrorCode.DUPLICATE_EMAIL);
        }
        if (userRepository.existsByNickname(req.getNickname())) {
            throw new CustomException(AuthErrorCode.DUPLICATE_NICKNAME);
        }

        // 2. User 생성
        User user = User.builder()
                .email(req.getEmail())
                .password(passwordEncoder.encode(req.getPassword()))
                .nickname(req.getNickname())
                .birthday(req.getBirthday())
                .gender(req.getGender())
                .dialect(req.getDialect())
                .build();

        // existsBy 검사 이후 save 사이의 Race Condition 방어:
        // DB Unique Constraint 위반 시 메시지 파싱 없이, existsBy를 재조회해 정확한 에러 반환
        User savedUser;
        try {
            savedUser = userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            // 동시 요청으로 사전 검사 통과 후 save 시점에 Unique 위반 발생 가능
            // 메시지 파싱 없이 재조회로 어느 필드인지 판별
            if (userRepository.existsByEmail(req.getEmail())) {
                throw new CustomException(AuthErrorCode.DUPLICATE_EMAIL);
            }
            if (userRepository.existsByNickname(req.getNickname())) {
                throw new CustomException(AuthErrorCode.DUPLICATE_NICKNAME);
            }
            throw e; // 그 외 예상치 못한 DB 에러는 그대로
        }

        // 3. 약관 동의 처리
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

        // 4. 응답
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

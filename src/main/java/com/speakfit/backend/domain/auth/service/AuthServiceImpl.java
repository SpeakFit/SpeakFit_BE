package com.speakfit.backend.domain.auth.service;

import com.speakfit.backend.domain.auth.dto.req.SignUpReq;
import com.speakfit.backend.domain.auth.dto.res.SignUpRes;
import com.speakfit.backend.domain.style.entity.SpeechStyle;
import com.speakfit.backend.domain.style.repository.SpeechStyleRepository;
import com.speakfit.backend.domain.term.entity.Term;
import com.speakfit.backend.domain.term.entity.mapping.UserTerm;
import com.speakfit.backend.domain.term.enums.TermType;
import com.speakfit.backend.domain.term.repository.TermRepository;
import com.speakfit.backend.domain.term.repository.UserTermRepository;
import com.speakfit.backend.domain.user.entity.User;
import com.speakfit.backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
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
public class AuthServiceImpl implements AuthService{

    private final UserRepository userRepository;
    private final SpeechStyleRepository speechStyleRepository;
    private final TermRepository termRepository;
    private final UserTermRepository userTermRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public SignUpRes signUp(SignUpReq.Request req){

        // 1. 중복 체크
        if (userRepository.existsByUsersId(req.getUsersId())) {
            throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
        }

        if (userRepository.existsByNickname(req.getNickname())) {
            throw new IllegalArgumentException("이미 사용 중인 닉네임입니다.");
        }

        if (userRepository.existsByPhoneNum(req.getPhoneNum())) {
            throw new IllegalArgumentException("이미 사용 중인 전화번호입니다.");
        }

        // 2. 스타일 조회
        SpeechStyle style = speechStyleRepository.findById(req.getStyleId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 styleId"));

        // 3. User 생성
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

        User savedUser = userRepository.save(user);

        // 4. 약관 동의 처리
        Map<TermType, Boolean> agreedMap =
                req.getTerms().stream()
                        .collect(Collectors.toMap(
                                SignUpReq.TermAgreement::getTermType,
                                SignUpReq.TermAgreement::getAgreed,
                                (a, b) -> b
                        ));
        List<Term> allTerms = termRepository.findAll();

        for(Term term : allTerms){
            if(term.isRequired()){
                Boolean agreed = agreedMap.get(term.getTermType());
                if(agreed == null || !agreed){
                    throw new IllegalArgumentException(
                            "필수 약관 미동의 " + term.getTermType()
                    );
                }
            }
        }

        List<UserTerm> userTerms = allTerms.stream()
                .map(term -> UserTerm.builder()
                        .user(savedUser)
                        .term(term)
                        .agreed(Boolean.TRUE.equals(
                                agreedMap.get(term.getTermType())
                        ))
                        .build()
                )
                .toList();

        userTermRepository.saveAll(userTerms);

        // 5. 응답
        return SignUpRes.builder()
                .userId(savedUser.getId())
                .usersId(savedUser.getUsersId())
                .nickname(savedUser.getNickname())
                .build();
    }
}

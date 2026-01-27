package com.speakfit.backend.domain.user.repository;

import com.speakfit.backend.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // 아이디 중복 체크
    boolean existsByUsersId(String usersId);

    // 닉네임 중복 체크
    boolean existsByNickname(String nickname);

    // 전화번호 중복 체크
    boolean existsByPhoneNum(String phoneNum);

    Optional<User> findByUsersId(String usersId);

}

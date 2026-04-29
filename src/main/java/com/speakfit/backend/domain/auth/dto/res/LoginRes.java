package com.speakfit.backend.domain.auth.dto.res;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.speakfit.backend.domain.user.entity.User;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LoginRes {

    private String accessToken;
    private UserInfo user;

    @JsonIgnore
    private String refreshToken;

    @JsonIgnore
    private long refreshTokenMaxAgeSeconds;

    @Getter
    @Builder
    public static class UserInfo{
        private Long userId;
        private String email;
        private String nickname;
        private String birthday;
        private String gender;
        private String dialect;

        public static UserInfo from(User user){
            return UserInfo.builder()
                    .userId(user.getId())
                    .email(user.getEmail())
                    .nickname(user.getNickname())
                    .birthday(user.getBirthday())
                    .gender(user.getGender().name())
                    .dialect(user.getDialect().name())
                    .build();
        }
    }
}

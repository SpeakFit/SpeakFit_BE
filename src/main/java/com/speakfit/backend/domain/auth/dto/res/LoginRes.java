package com.speakfit.backend.domain.auth.dto.res;

import com.speakfit.backend.domain.user.entity.User;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class LoginRes {

    private String accessToken;
    private UserInfo user;

    @Getter
    @Builder
    public static class UserInfo{
        private Long userId;
        private String usersId;
        private String name;
        private String nickname;
        private String phoneNum;
        private LocalDate birth;
        private String gender;
        private String dialect;
        private Long styleId;

        public static UserInfo from(User user){
            return UserInfo.builder()
                    .userId(user.getId())
                    .usersId(user.getUsersId())
                    .name(user.getName())
                    .nickname(user.getNickname())
                    .phoneNum(user.getPhoneNum())
                    .birth(user.getBirth())
                    .gender(user.getGender().name())
                    .dialect(user.getDialect().name())
                    .styleId(user.getStyle().getId())
                    .build();
        }
    }
}

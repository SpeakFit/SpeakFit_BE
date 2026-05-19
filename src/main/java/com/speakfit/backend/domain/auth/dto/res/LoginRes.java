package com.speakfit.backend.domain.auth.dto.res;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.speakfit.backend.domain.user.entity.DefaultVoice;
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
        private DefaultVoiceInfo defaultVoice;

        public static UserInfo from(User user){
            return UserInfo.builder()
                    .userId(user.getId())
                    .email(user.getEmail())
                    .nickname(user.getNickname())
                    .birthday(user.getBirthday())
                    .gender(user.getGender().name())
                    .dialect(user.getDialect().name())
                    .defaultVoice(DefaultVoiceInfo.from(user.getDefaultVoice()))
                    .build();
        }
    }

    @Getter
    @Builder
    public static class DefaultVoiceInfo {
        private Double defaultPitch;
        private Double defaultWpm;

        public static DefaultVoiceInfo from(DefaultVoice defaultVoice) {
            if (defaultVoice == null) {
                return null;
            }

            return DefaultVoiceInfo.builder()
                    .defaultPitch(defaultVoice.getDefaultPitch())
                    .defaultWpm(defaultVoice.getDefaultWpm())
                    .build();
        }
    }
}

package com.speakfit.backend.domain.style.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.speakfit.backend.domain.style.dto.res.SpeechStylesGetRes;
import com.speakfit.backend.domain.style.entity.SpeechStyle;
import com.speakfit.backend.domain.style.enums.StyleType;
import com.speakfit.backend.domain.style.repository.SpeechStyleRepository;
import com.speakfit.backend.domain.user.entity.User;
import com.speakfit.backend.domain.user.enums.Gender;
import com.speakfit.backend.domain.user.repository.UserRepository;
import com.speakfit.backend.global.infra.s3.S3Service;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("스피치 스타일 샘플 음원의 서명 URL")
class SpeechStylePresignTest {

    private SpeechStylesGetRes stylesFor(Gender gender) {
        SpeechStyleRepository styleRepository = mock(SpeechStyleRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        S3Service s3Service = mock(S3Service.class);
        when(s3Service.presignGet(anyString())).thenAnswer(inv -> "signed:" + inv.getArgument(0));

        User user = User.builder().email("a@b.com").password("pw").nickname("n")
                .birthday("2000-01-01").gender(gender).build();
        ReflectionTestUtils.setField(user, "id", 1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(styleRepository.findAllByOrderBySortOrderAscIdAsc()).thenReturn(List.of(SpeechStyle.builder()
                .styleType(StyleType.DELIVERY).displayName("전달력 있는").description("d")
                .sampleAudioUrlMale("samples/styles/delivery_male.mp3")
                .sampleAudioUrlFemale("samples/styles/delivery_female.mp3")
                .sortOrder(1).build()));

        return new SpeechStyleQueryServiceImpl(styleRepository, userRepository, s3Service).getStyles(1L);
    }

    @Test
    @DisplayName("사용자 성별에 맞는 샘플 음원을 서명 URL 로 내려 준다")
    void signsGenderSpecificSample() {
        assertThat(stylesFor(Gender.MALE).getStyles().get(0).getSampleAudioUrl())
                .isEqualTo("signed:samples/styles/delivery_male.mp3");
        assertThat(stylesFor(Gender.FEMALE).getStyles().get(0).getSampleAudioUrl())
                .isEqualTo("signed:samples/styles/delivery_female.mp3");
    }
}

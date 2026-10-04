package com.speakfit.backend.domain.voice.service;

import com.speakfit.backend.domain.user.entity.User;
import com.speakfit.backend.domain.user.enums.Gender;
import com.speakfit.backend.domain.user.repository.UserRepository;
import com.speakfit.backend.domain.voice.dto.res.VoiceAnalysisResultRes;
import com.speakfit.backend.domain.voice.entity.BaselineVoice;
import com.speakfit.backend.domain.voice.exception.VoiceException;
import com.speakfit.backend.domain.voice.exception.VoiceExceptionStatus;
import com.speakfit.backend.domain.voice.repository.BaselineVoiceRepository;
import com.speakfit.backend.global.infra.s3.S3Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 음색 분석 결과 조회는 본인 소유 데이터만 반환해야 한다. */
class VoiceAnalysisOwnershipTest {

    private static final long OWNER_ID = 1L;
    private static final long OTHER_USER_ID = 2L;
    private static final long ANALYSIS_ID = 10L;

    private final BaselineVoiceRepository baselineVoiceRepository = mock(BaselineVoiceRepository.class);
    private VoiceAnalysisServiceImpl service;
    private BaselineVoice baselineVoice;

    @BeforeEach
    void setUp() {
        service = new VoiceAnalysisServiceImpl(
                mock(S3Service.class),
                mock(WebClient.class),
                mock(UserRepository.class),
                baselineVoiceRepository);

        User owner = User.builder()
                .email("a@b.com").password("pw").nickname("nick")
                .birthday("2000-01-01").gender(Gender.MALE).build();
        ReflectionTestUtils.setField(owner, "id", OWNER_ID);

        baselineVoice = BaselineVoice.builder().user(owner).build();
        ReflectionTestUtils.setField(baselineVoice, "id", ANALYSIS_ID);
        baselineVoice.complete(180.0, 250.0, 0.5, 0.1, 0.2, "{}");

        // 소유자 조건(id + userId)이 맞을 때만 조회되는 레포지토리 동작을 흉내 낸다.
        when(baselineVoiceRepository.findByIdAndUserId(ANALYSIS_ID, OWNER_ID)).thenReturn(Optional.of(baselineVoice));
        when(baselineVoiceRepository.findByIdAndUserId(ANALYSIS_ID, OTHER_USER_ID)).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("본인의 분석 결과는 조회된다")
    void ownerCanRead() {
        VoiceAnalysisResultRes res = service.getVoiceAnalysisResult(ANALYSIS_ID, OWNER_ID);

        assertThat(res.getAnalysisId()).isEqualTo(ANALYSIS_ID);
        assertThat(res.getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("다른 사용자의 분석 결과 id 로 조회하면 404(VOICE_ANALYSIS_NOT_FOUND) 이다")
    void otherUserGetsNotFound() {
        assertThatThrownBy(() -> service.getVoiceAnalysisResult(ANALYSIS_ID, OTHER_USER_ID))
                .isInstanceOf(VoiceException.class)
                .extracting(e -> ((VoiceException) e).getStatus())
                .isEqualTo(VoiceExceptionStatus.VOICE_ANALYSIS_NOT_FOUND);
    }

    @Test
    @DisplayName("존재하지 않는 id 도 타인 id 와 같은 404 로 응답한다 (존재 여부 노출 방지)")
    void unknownIdLooksTheSame() {
        when(baselineVoiceRepository.findByIdAndUserId(999L, OWNER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getVoiceAnalysisResult(999L, OWNER_ID))
                .isInstanceOf(VoiceException.class)
                .extracting(e -> ((VoiceException) e).getStatus())
                .isEqualTo(VoiceExceptionStatus.VOICE_ANALYSIS_NOT_FOUND);
    }
}

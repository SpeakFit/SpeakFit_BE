package com.speakfit.backend.domain.voice.service;

import com.speakfit.backend.domain.user.entity.User;
import com.speakfit.backend.domain.user.enums.Gender;
import com.speakfit.backend.domain.user.repository.UserRepository;
import com.speakfit.backend.domain.voice.entity.BaselineVoice;
import com.speakfit.backend.domain.voice.exception.VoiceException;
import com.speakfit.backend.domain.voice.exception.VoiceExceptionStatus;
import com.speakfit.backend.domain.voice.repository.BaselineVoiceRepository;
import com.speakfit.backend.global.infra.s3.S3Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.net.URI;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 파이썬 분석 서버를 호출하지 못했을 때 사용자 책임(400)이 아니라 서버 측 오류로 응답해야 한다. */
class VoiceAnalysisPythonFailureTest {

    private static final long USER_ID = 1L;

    private final S3Service s3Service = mock(S3Service.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final BaselineVoiceRepository baselineVoiceRepository = mock(BaselineVoiceRepository.class);
    private BaselineVoice savedBaseline;

    @BeforeEach
    void setUp() throws Exception {
        User user = User.builder()
                .email("a@b.com").password("pw").nickname("nick")
                .birthday("2000-01-01").gender(Gender.MALE).build();
        ReflectionTestUtils.setField(user, "id", USER_ID);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(baselineVoiceRepository.save(any(BaselineVoice.class))).thenAnswer(inv -> {
            savedBaseline = inv.getArgument(0);
            return savedBaseline;
        });
        when(s3Service.upload(any(), anyString())).thenReturn("voice/baseline/1/x.wav");
    }

    private VoiceAnalysisServiceImpl serviceWith(Throwable pythonError) {
        WebClient client = WebClient.builder()
                .exchangeFunction(request -> Mono.error(pythonError))
                .baseUrl("http://localhost:5000")
                .build();
        VoiceAnalysisServiceImpl service = new VoiceAnalysisServiceImpl(
                s3Service, client, userRepository, baselineVoiceRepository);
        ReflectionTestUtils.setField(service, "minVoiceFileSizeBytes", 5120L);
        ReflectionTestUtils.setField(service, "analysisTimeoutSeconds", 5L);
        return service;
    }

    private static MockMultipartFile voiceFile() {
        return new MockMultipartFile("voiceFile", "voice.wav", "audio/wav", new byte[10_000]);
    }

    private static WebClientRequestException requestException(Throwable cause) {
        return new WebClientRequestException(
                cause, HttpMethod.POST, URI.create("http://localhost:5000/voice-analysis"), HttpHeaders.EMPTY);
    }

    @Test
    @DisplayName("파이썬 서버에 연결할 수 없으면 400 이 아니라 502(VOICE_ANALYSIS_FAILED) 이다")
    void connectionRefusedIsServerError() {
        VoiceAnalysisServiceImpl service = serviceWith(requestException(new ConnectException("Connection refused")));

        assertThatThrownBy(() -> service.requestVoiceAnalysis(voiceFile(), USER_ID))
                .isInstanceOf(VoiceException.class)
                .extracting(e -> ((VoiceException) e).getStatus())
                .isEqualTo(VoiceExceptionStatus.VOICE_ANALYSIS_FAILED);
        assertThat(savedBaseline.getStatus().name()).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("파이썬 서버 응답 대기 시간이 초과되면 504(VOICE_ANALYSIS_TIMEOUT) 이다")
    void timeoutStaysGatewayTimeout() {
        VoiceAnalysisServiceImpl service = serviceWith(requestException(new TimeoutException("read timeout")));

        assertThatThrownBy(() -> service.requestVoiceAnalysis(voiceFile(), USER_ID))
                .isInstanceOf(VoiceException.class)
                .extracting(e -> ((VoiceException) e).getStatus())
                .isEqualTo(VoiceExceptionStatus.VOICE_ANALYSIS_TIMEOUT);
    }

    @Test
    @DisplayName("업로드한 파일이 너무 작으면 여전히 400(VOICE_DATA_INSUFFICIENT) 이다")
    void tooSmallFileIsStillBadRequest() {
        VoiceAnalysisServiceImpl service = serviceWith(requestException(new ConnectException("unused")));
        MockMultipartFile tiny = new MockMultipartFile("voiceFile", "voice.wav", "audio/wav", new byte[100]);

        assertThatThrownBy(() -> service.requestVoiceAnalysis(tiny, USER_ID))
                .isInstanceOf(VoiceException.class)
                .extracting(e -> ((VoiceException) e).getStatus())
                .isEqualTo(VoiceExceptionStatus.VOICE_DATA_INSUFFICIENT);
    }
}

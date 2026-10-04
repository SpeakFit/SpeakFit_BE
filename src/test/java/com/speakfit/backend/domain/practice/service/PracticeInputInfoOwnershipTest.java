package com.speakfit.backend.domain.practice.service;

import com.speakfit.backend.domain.metric.service.TargetMetricCalculator;
import com.speakfit.backend.domain.practice.dto.req.InputPracticeInfoReq;
import com.speakfit.backend.domain.practice.enums.AudienceType;
import com.speakfit.backend.domain.practice.enums.AudienceUnderstanding;
import com.speakfit.backend.domain.practice.enums.SpeechInformation;
import com.speakfit.backend.domain.practice.repository.*;
import com.speakfit.backend.domain.script.entity.Script;
import com.speakfit.backend.domain.script.exception.ScriptErrorCode;
import com.speakfit.backend.domain.script.repository.PptSlideRepository;
import com.speakfit.backend.domain.script.repository.ScriptRepository;
import com.speakfit.backend.domain.script.service.ScriptContentParser;
import com.speakfit.backend.domain.style.exception.SpeechStyleErrorCode;
import com.speakfit.backend.domain.style.repository.SpeechStyleRepository;
import com.speakfit.backend.domain.user.entity.User;
import com.speakfit.backend.domain.user.enums.Gender;
import com.speakfit.backend.domain.user.repository.UserRepository;
import com.speakfit.backend.global.apiPayload.exception.CustomException;
import com.speakfit.backend.global.infra.jwt.JwtProvider;
import com.speakfit.backend.global.infra.s3.S3Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 연습 정보 입력(POST /api/scripts/{scriptId})은 본인 대본에 대해서만 연습 기록을 만들 수 있다. */
class PracticeInputInfoOwnershipTest {

    private static final long OWNER_ID = 1L;
    private static final long OTHER_USER_ID = 2L;
    private static final long SCRIPT_ID = 10L;

    private final PracticeRepository practiceRepository = mock(PracticeRepository.class);
    private final ScriptRepository scriptRepository = mock(ScriptRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final SpeechStyleRepository speechStyleRepository = mock(SpeechStyleRepository.class);

    private PracticeServiceImpl service;
    private InputPracticeInfoReq.Request request;

    @BeforeEach
    void setUp() {
        service = new PracticeServiceImpl(
                practiceRepository, scriptRepository, userRepository,
                mock(PptSlideRepository.class), speechStyleRepository,
                mock(AnalysisResultRepository.class), mock(AiAnalysisResultRepository.class),
                mock(PracticeIssueRepository.class), mock(PracticeDetailRepository.class),
                mock(PracticeSentenceResultRepository.class),
                mock(AiAnalysisService.class), mock(PracticeTxService.class),
                mock(ScriptContentParser.class), mock(JwtProvider.class),
                mock(TargetMetricCalculator.class), mock(S3Service.class));

        User owner = user(OWNER_ID);
        Script script = Script.builder().user(owner).build();
        ReflectionTestUtils.setField(script, "id", SCRIPT_ID);

        when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));
        when(userRepository.findById(OTHER_USER_ID)).thenReturn(Optional.of(user(OTHER_USER_ID)));
        when(scriptRepository.findByIdWithUser(SCRIPT_ID)).thenReturn(Optional.of(script));
        when(practiceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(speechStyleRepository.findAllByOrderBySortOrderAscIdAsc()).thenReturn(List.of());

        request = new InputPracticeInfoReq.Request();
        ReflectionTestUtils.setField(request, "audienceType", AudienceType.ADULT);
        ReflectionTestUtils.setField(request, "audienceUnderstanding", AudienceUnderstanding.MIDDLE);
        ReflectionTestUtils.setField(request, "speechInformation", SpeechInformation.PRESENTATION);
        ReflectionTestUtils.setField(request, "targetTime", 5);
    }

    private User user(long id) {
        User u = User.builder().email(id + "@b.com").password("pw").nickname("n" + id)
                .birthday("2000-01-01").gender(Gender.MALE).build();
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    @Test
    @DisplayName("다른 사용자의 대본으로는 연습 기록을 만들 수 없다")
    void otherUsersScriptIsRejected() {
        assertThatThrownBy(() -> service.inputPracticeInfo(SCRIPT_ID, request, OTHER_USER_ID))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ScriptErrorCode.SCRIPT_ACCESS_DENIED);

        verify(practiceRepository, never()).save(any());
    }

    @Test
    @DisplayName("본인 대본이면 소유자 검사를 통과해 연습 기록을 만든다")
    void ownScriptPassesOwnershipCheck() {
        // 스타일 목록을 비워 두었으므로 기록 저장 이후 STYLES_EMPTY 로 끝난다. (소유자 검사를 통과했는지만 확인)
        assertThatThrownBy(() -> service.inputPracticeInfo(SCRIPT_ID, request, OWNER_ID))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(SpeechStyleErrorCode.STYLES_EMPTY);

        verify(practiceRepository).save(any());
    }

    @Test
    @DisplayName("존재하지 않는 대본은 SCRIPT_NOT_FOUND")
    void unknownScript() {
        when(scriptRepository.findByIdWithUser(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.inputPracticeInfo(999L, request, OWNER_ID))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ScriptErrorCode.SCRIPT_NOT_FOUND);
    }
}

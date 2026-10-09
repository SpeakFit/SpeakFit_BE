package com.speakfit.backend.domain.script.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.speakfit.backend.domain.practice.repository.PracticeRepository;
import com.speakfit.backend.domain.practice.service.AiAnalysisService;
import com.speakfit.backend.domain.script.dto.res.GetScriptDetailRes;
import com.speakfit.backend.domain.script.dto.res.UploadPptRes;
import com.speakfit.backend.domain.script.entity.PptSlide;
import com.speakfit.backend.domain.script.entity.Script;
import com.speakfit.backend.domain.script.repository.ScriptRepository;
import com.speakfit.backend.domain.user.entity.User;
import com.speakfit.backend.domain.user.enums.Gender;
import com.speakfit.backend.domain.user.repository.UserRepository;
import com.speakfit.backend.global.infra.s3.S3Service;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

/** 비공개 버킷이므로 대본 응답의 PPT/슬라이드 URL 은 서명 URL 로 내려가야 한다. */
@DisplayName("대본 응답의 서명 URL")
class PresignedResponseTest {

    private static final long USER_ID = 1L;
    private static final long SCRIPT_ID = 10L;
    private static final String PPT_URL = "https://b.s3.ap-northeast-2.amazonaws.com/ppt/10/attempts/a/source.pptx";
    private static final String SLIDE_URL = "https://b.s3.ap-northeast-2.amazonaws.com/ppt/10/attempts/a/slides/1.png";

    private final S3Service s3Service = mock(S3Service.class);
    private ScriptServiceImpl service;

    @BeforeEach
    void setUp() {
        when(s3Service.presignGet(anyString())).thenAnswer(inv -> "signed:" + inv.getArgument(0));

        ScriptRepository scriptRepository = mock(ScriptRepository.class);
        service = new ScriptServiceImpl(
                scriptRepository, mock(PracticeRepository.class), mock(AiAnalysisService.class),
                mock(ScriptTxService.class), mock(UserRepository.class),
                mock(WebClient.class), mock(WebClient.class), mock(PptConvertAsyncService.class), s3Service);

        User owner = User.builder().email("a@b.com").password("pw").nickname("n")
                .birthday("2000-01-01").gender(Gender.MALE).build();
        ReflectionTestUtils.setField(owner, "id", USER_ID);
        Script script = Script.builder().user(owner).title("t").content("c").build();
        ReflectionTestUtils.setField(script, "id", SCRIPT_ID);
        script.updatePptInfo(PPT_URL, 1);
        script.getPptSlides().add(PptSlide.builder().script(script).imageUrl(SLIDE_URL).slideIndex(1).build());
        when(scriptRepository.findByIdWithUser(SCRIPT_ID)).thenReturn(Optional.of(script));
    }

    @Test
    @DisplayName("대본 상세의 PPT 원본과 슬라이드 이미지는 서명 URL 이다")
    void scriptDetailIsSigned() {
        GetScriptDetailRes.PptInfoRes info = service.getScript(SCRIPT_ID, USER_ID).getPptInfo();

        assertThat(info.getPptUrl()).isEqualTo("signed:" + PPT_URL);
        assertThat(info.getSlides()).extracting(GetScriptDetailRes.PptSlideRes::getImageUrl)
                .containsExactly("signed:" + SLIDE_URL);
    }

    @Test
    @DisplayName("PPT 변환 상태 조회의 원본과 슬라이드 이미지도 서명 URL 이다")
    void pptStatusIsSigned() {
        UploadPptRes.PptInfoRes info = service.getPptStatus(SCRIPT_ID, USER_ID).getPptInfo();

        assertThat(info.getSourcePptUrl()).isEqualTo("signed:" + PPT_URL);
        assertThat(info.getSlides()).extracting(UploadPptRes.PptSlideRes::getImageUrl)
                .containsExactly("signed:" + SLIDE_URL);
    }

    @Test
    @DisplayName("DB 에 저장된 값은 그대로 두고 응답에서만 서명한다")
    void storedValueIsUntouched() {
        service.getScript(SCRIPT_ID, USER_ID);

        // 서명 대상은 원본 URL(저장값)이며, 서명된 값을 다시 저장하지 않는다
        org.mockito.Mockito.verify(s3Service).presignGet(PPT_URL);
        org.mockito.Mockito.verify(s3Service).presignGet(SLIDE_URL);
    }
}

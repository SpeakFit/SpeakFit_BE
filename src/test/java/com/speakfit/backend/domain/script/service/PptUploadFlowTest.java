package com.speakfit.backend.domain.script.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.speakfit.backend.domain.practice.repository.PracticeRepository;
import com.speakfit.backend.domain.practice.service.AiAnalysisService;
import com.speakfit.backend.domain.script.entity.Script;
import com.speakfit.backend.domain.script.exception.ScriptErrorCode;
import com.speakfit.backend.domain.script.repository.ScriptRepository;
import com.speakfit.backend.domain.user.entity.User;
import com.speakfit.backend.domain.user.enums.Gender;
import com.speakfit.backend.domain.user.repository.UserRepository;
import com.speakfit.backend.global.apiPayload.exception.CustomException;
import com.speakfit.backend.global.infra.s3.S3Service;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

/** PPT 업로드는 로컬 디스크가 아니라 S3 를 거쳐 분석 서버로 전달되어야 한다. */
@DisplayName("PPT 업로드 흐름 (S3)")
class PptUploadFlowTest {

    private static final long USER_ID = 1L;
    private static final long SCRIPT_ID = 10L;
    private static final String SOURCE_URL =
            "https://speakfit-prod-1.s3.ap-northeast-2.amazonaws.com/ppt/10/attempts/x/1_slide.pptx";

    private final ScriptRepository scriptRepository = mock(ScriptRepository.class);
    private final ScriptTxService scriptTxService = mock(ScriptTxService.class);
    private final PptConvertAsyncService pptConvertAsyncService = mock(PptConvertAsyncService.class);
    private final S3Service s3Service = mock(S3Service.class);
    private ScriptServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ScriptServiceImpl(
                scriptRepository, mock(PracticeRepository.class), mock(AiAnalysisService.class),
                scriptTxService, mock(UserRepository.class),
                mock(WebClient.class), mock(WebClient.class), pptConvertAsyncService, s3Service);

        User owner = User.builder().email("a@b.com").password("pw").nickname("n")
                .birthday("2000-01-01").gender(Gender.MALE).build();
        ReflectionTestUtils.setField(owner, "id", USER_ID);
        Script script = Script.builder().user(owner).title("t").content("c").build();
        ReflectionTestUtils.setField(script, "id", SCRIPT_ID);
        when(scriptRepository.findByIdWithUser(SCRIPT_ID)).thenReturn(Optional.of(script));
    }

    private static MockMultipartFile pptx() {
        return new MockMultipartFile("file", "slide.pptx",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation", new byte[100]);
    }

    @Test
    @DisplayName("원본을 S3 에 올리고, 같은 URL 과 prefix 로 변환을 요청한다")
    void uploadsToS3ThenConverts() throws Exception {
        when(s3Service.upload(any(), anyString())).thenReturn(SOURCE_URL);

        service.uploadPpt(SCRIPT_ID, pptx(), USER_ID);

        ArgumentCaptor<String> prefixOnUpload = ArgumentCaptor.forClass(String.class);
        verify(s3Service).upload(any(), prefixOnUpload.capture());
        assertThat(prefixOnUpload.getValue()).startsWith("ppt/10/attempts/");

        verify(scriptTxService).markPptProcessing(SCRIPT_ID, USER_ID);
        verify(pptConvertAsyncService).convertPptAsync(
                eq(SCRIPT_ID), eq(USER_ID), eq(SOURCE_URL), eq(prefixOnUpload.getValue()), any());
    }

    @Test
    @DisplayName("S3 업로드가 실패하면 변환 상태로 바꾸지 않고 업로드 실패로 응답한다")
    void s3FailureDoesNotMarkProcessing() throws Exception {
        when(s3Service.upload(any(), anyString())).thenThrow(new RuntimeException("s3 down"));

        assertThatThrownBy(() -> service.uploadPpt(SCRIPT_ID, pptx(), USER_ID))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ScriptErrorCode.SCRIPT_PPT_UPLOAD_FAILED);
        verify(scriptTxService, never()).markPptProcessing(anyLong(), anyLong());
        verify(pptConvertAsyncService, never()).convertPptAsync(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("변환 대기열이 가득 차면 올려 둔 S3 파일을 지우고 실패로 표시한다")
    void queueFullCleansUpS3() throws Exception {
        when(s3Service.upload(any(), anyString())).thenReturn(SOURCE_URL);
        doThrow(new TaskRejectedException("full")).when(pptConvertAsyncService)
                .convertPptAsync(any(), any(), any(), any(), any());

        assertThatThrownBy(() -> service.uploadPpt(SCRIPT_ID, pptx(), USER_ID))
                .isInstanceOf(CustomException.class);

        verify(s3Service).deleteByPrefix(org.mockito.ArgumentMatchers.startsWith("ppt/10/attempts/"));
        verify(scriptTxService).markPptFailed(eq(SCRIPT_ID), eq(USER_ID), anyString());
    }

    @Test
    @DisplayName("허용되지 않는 확장자는 S3 에 올리지 않는다")
    void rejectsInvalidExtensionBeforeUpload() throws Exception {
        MockMultipartFile exe = new MockMultipartFile("file", "virus.exe", "application/octet-stream", new byte[10]);

        assertThatThrownBy(() -> service.uploadPpt(SCRIPT_ID, exe, USER_ID))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ScriptErrorCode.SCRIPT_PPT_INVALID_EXTENSION);
        verify(s3Service, never()).upload(any(), anyString());
    }

    @Test
    @DisplayName("대본을 삭제하면 해당 대본의 PPT S3 파일도 지운다")
    void deleteScriptRemovesS3Files() {
        service.deleteScript(SCRIPT_ID, USER_ID);

        verify(s3Service).deleteByPrefix("ppt/10");
    }

    @Test
    @DisplayName("S3 삭제가 실패해도 대본 삭제는 성공한다")
    void deleteScriptSucceedsEvenIfS3DeleteFails() {
        doThrow(new RuntimeException("s3 down")).when(s3Service).deleteByPrefix(anyString());

        assertThat(service.deleteScript(SCRIPT_ID, USER_ID).getId()).isEqualTo(SCRIPT_ID);
    }
}

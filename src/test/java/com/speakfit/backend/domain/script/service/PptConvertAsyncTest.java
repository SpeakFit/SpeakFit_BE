package com.speakfit.backend.domain.script.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.speakfit.backend.global.infra.s3.S3Service;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/** 분석 서버에는 로컬 경로가 아니라 S3 URL/prefix 를 보내고, 결과에 따라 S3 파일을 정리한다. */
@DisplayName("PPT 변환 비동기 처리 (S3)")
class PptConvertAsyncTest {

    private static final String ATTEMPT = "ppt/10/attempts/new";
    private static final String SOURCE_URL =
            "https://b.s3.ap-northeast-2.amazonaws.com/ppt/10/attempts/new/1_slide.pptx";
    private static final String PREVIOUS_URL =
            "https://b.s3.ap-northeast-2.amazonaws.com/ppt/10/attempts/old/file.pptx";
    private static final String OK_BODY = """
            {"sourcePptUrl":"%s","totalSlides":1,"slides":[{"page":1,"imageUrl":"https://b/ppt/10/attempts/new/slides/1.png"}]}
            """.formatted(SOURCE_URL);

    private final ScriptTxService scriptTxService = mock(ScriptTxService.class);
    private final S3Service s3Service = mock(S3Service.class);
    private final AtomicReference<String> requestBody = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        when(s3Service.extractObjectKey(PREVIOUS_URL)).thenReturn("ppt/10/attempts/old/file.pptx");
    }

    private PptConvertAsyncServiceImpl serviceReturning(HttpStatus status, String body) {
        WebClient client = WebClient.builder()
                .baseUrl("http://localhost:5000")
                .exchangeFunction(request -> {
                    // 요청 본문을 읽어 두려면 실제로 직렬화해야 하므로, 여기서는 URI 만 확인한다.
                    requestBody.set(request.url().getPath());
                    return Mono.just(ClientResponse.create(status)
                            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .body(body).build());
                })
                .build();
        return new PptConvertAsyncServiceImpl(scriptTxService, client, s3Service);
    }

    @Test
    @DisplayName("성공하면 결과를 저장하고 이전 변환 시도의 S3 파일을 지운다")
    void successSavesAndDeletesPreviousAttempt() {
        serviceReturning(HttpStatus.OK, OK_BODY).convertPptAsync(10L, 1L, SOURCE_URL, ATTEMPT, PREVIOUS_URL);

        assertThat(requestBody.get()).isEqualTo("/ppt/convert");
        verify(scriptTxService).savePptSuccess(eq(10L), eq(1L), eq(SOURCE_URL), eq(1), any());
        verify(s3Service).deleteByPrefix("ppt/10/attempts/old");
        verify(s3Service, never()).deleteByPrefix(ATTEMPT);
        verify(scriptTxService, never()).markPptFailed(any(), any(), anyString());
    }

    @Test
    @DisplayName("이전 파일이 없으면 아무것도 지우지 않는다")
    void noPreviousFileMeansNothingToDelete() {
        serviceReturning(HttpStatus.OK, OK_BODY).convertPptAsync(10L, 1L, SOURCE_URL, ATTEMPT, null);

        verify(s3Service, never()).deleteByPrefix(anyString());
    }

    @Test
    @DisplayName("분석 서버가 실패하면 이번 시도의 S3 파일을 지우고 실패로 표시한다 (이전 파일은 유지)")
    void failureCleansCurrentAttemptOnly() {
        serviceReturning(HttpStatus.INTERNAL_SERVER_ERROR, "{}")
                .convertPptAsync(10L, 1L, SOURCE_URL, ATTEMPT, PREVIOUS_URL);

        verify(s3Service).deleteByPrefix(ATTEMPT);
        verify(s3Service, never()).deleteByPrefix("ppt/10/attempts/old");
        verify(scriptTxService).markPptFailed(eq(10L), eq(1L), anyString());
        verify(scriptTxService, never()).savePptSuccess(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("S3 정리에 실패해도 실패 상태 저장은 계속된다")
    void cleanupFailureDoesNotBlockFailedState() {
        org.mockito.Mockito.doThrow(new RuntimeException("s3 down")).when(s3Service).deleteByPrefix(anyString());

        serviceReturning(HttpStatus.INTERNAL_SERVER_ERROR, "{}")
                .convertPptAsync(10L, 1L, SOURCE_URL, ATTEMPT, null);

        verify(scriptTxService).markPptFailed(eq(10L), eq(1L), anyString());
    }

    @Test
    @DisplayName("분석 서버에는 pptUrl 과 outputPrefix 만 보내고 로컬 경로는 보내지 않는다 (파이썬 요청 모델과 같은 필드명)")
    void sendsS3ContractToPythonServer() throws Exception {
        AtomicReference<String> receivedBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ppt/convert", exchange -> {
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = OK_BODY.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            WebClient client = WebClient.builder()
                    .baseUrl("http://127.0.0.1:" + server.getAddress().getPort()).build();
            new PptConvertAsyncServiceImpl(scriptTxService, client, s3Service)
                    .convertPptAsync(10L, 1L, SOURCE_URL, ATTEMPT, null);
        } finally {
            server.stop(0);
        }

        assertThat(receivedBody.get())
                .contains("\"pptUrl\":\"" + SOURCE_URL + "\"")
                .contains("\"outputPrefix\":\"" + ATTEMPT + "\"")
                .doesNotContain("pptPath")
                .doesNotContain("outputDir");
        verify(scriptTxService).savePptSuccess(eq(10L), eq(1L), eq(SOURCE_URL), eq(1), any());
    }
}

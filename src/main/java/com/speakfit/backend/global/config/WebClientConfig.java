package com.speakfit.backend.global.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {

    /**
     * 일반 API 호출용 WebClient — 단기 응답 전용.
     * responseTimeout: 설정값(기본 60초) 그대로 유지.
     */
    @Bean("webClient")
    public WebClient webClient(@Value("${app.ai.base-url}") String aiBaseUrl,
                               @Value("${app.ai.connect-timeout-millis:5000}") int connectTimeoutMillis,
                               // 분석 파이프라인(STT 포함)이 길어질 수 있으므로 안전 기본값 120초.
                               // 값이 누락되거나 너무 짧으면 WebClient가 분석 응답을 중간에 끊을 수 있다.
                               @Value("${app.ai.response-timeout-seconds:120}") long responseTimeoutSeconds) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMillis)
                .responseTimeout(Duration.ofSeconds(responseTimeoutSeconds));

        return WebClient.builder()
                .baseUrl(aiBaseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    /**
     * [STEP-A] SSE 스트리밍 전용 WebClient.
     * - responseTimeout 미설정: 스트림이 열려 있는 동안 응답 완료를 기다리지 않음.
     * - ReadTimeout 5분: 5분 동안 새 데이터가 오지 않으면 연결 종료 (무한 대기 방지).
     * - 10분 분량 대본도 안전하게 스트리밍 가능.
     */
    @Bean("streamingWebClient")
    public WebClient streamingWebClient(
            @Value("${app.ai.base-url}") String aiBaseUrl,
            @Value("${app.ai.connect-timeout-millis:5000}") int connectTimeoutMillis,
            @Value("${app.ai.streaming-read-timeout-seconds:300}") long streamingReadTimeoutSeconds) {

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMillis)
                // responseTimeout 미설정 — 스트리밍 중 타임아웃 없음
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(streamingReadTimeoutSeconds, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(30, TimeUnit.SECONDS)));

        return WebClient.builder()
                .baseUrl(aiBaseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}

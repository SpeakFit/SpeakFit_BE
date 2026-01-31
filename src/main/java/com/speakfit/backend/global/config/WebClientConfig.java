package com.speakfit.backend.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient webClient() {
        // 1. 타임아웃 설정 (30초)
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(120));

        // 2. Base URL 설정 (http://localhost:5000)
        return WebClient.builder()
                .baseUrl("http://localhost:5000") // 기본 주소 고정
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
package com.speakfit.backend.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.InstanceProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

@DisplayName("S3 자격 증명 선택")
class S3ConfigTest {

    @Test
    @DisplayName("액세스 키와 시크릿 키가 모두 있으면 그 키를 쓴다 (로컬 개발)")
    void usesStaticKeysWhenBothPresent() {
        var provider = S3Config.credentialsProvider("AKIAEXAMPLE", "secret");

        assertThat(provider).isInstanceOf(StaticCredentialsProvider.class);
        assertThat(provider.resolveCredentials().accessKeyId()).isEqualTo("AKIAEXAMPLE");
    }

    @Test
    @DisplayName("키가 비어 있으면 기본 체인(EC2 IAM 역할 등)을 쓴다 (운영)")
    void fallsBackToDefaultChainWhenKeysAreBlank() {
        assertThat(S3Config.credentialsProvider("", "")).isInstanceOf(DefaultCredentialsProvider.class);
        assertThat(S3Config.credentialsProvider(null, null)).isInstanceOf(DefaultCredentialsProvider.class);
    }

    @Test
    @DisplayName("둘 중 하나만 있으면 불완전한 키를 쓰지 않고 기본 체인으로 간다")
    void partialKeysAreIgnored() {
        assertThat(S3Config.credentialsProvider("AKIAEXAMPLE", "")).isInstanceOf(DefaultCredentialsProvider.class);
        assertThat(S3Config.credentialsProvider("", "secret")).isInstanceOf(DefaultCredentialsProvider.class);
    }

    @Test
    @DisplayName("EC2 인스턴스 역할 자격 증명(IMDSv2)을 읽을 수 있다 - AWS SDK v1 은 Jackson 2.20 에서 NoSuchFieldError 로 실패했다")
    void readsInstanceRoleCredentialsFromMetadataService() throws Exception {
        HttpServer imds = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        imds.createContext("/latest/api/token", exchange -> respond(exchange, "test-token"));
        imds.createContext("/latest/meta-data/iam/security-credentials/", exchange -> {
            if (exchange.getRequestURI().getPath().endsWith("/speakfit-spring-ec2")) {
                respond(exchange, "{\"Code\":\"Success\",\"AccessKeyId\":\"ASIAEXAMPLE\","
                        + "\"SecretAccessKey\":\"secret\",\"Token\":\"token\","
                        + "\"Expiration\":\"2999-01-01T00:00:00Z\"}");
            } else {
                respond(exchange, "speakfit-spring-ec2");
            }
        });
        imds.start();
        try {
            var provider = InstanceProfileCredentialsProvider.builder()
                    .endpoint("http://127.0.0.1:" + imds.getAddress().getPort())
                    .build();

            assertThat(provider.resolveCredentials().accessKeyId()).isEqualTo("ASIAEXAMPLE");
        } finally {
            imds.stop(0);
        }
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}

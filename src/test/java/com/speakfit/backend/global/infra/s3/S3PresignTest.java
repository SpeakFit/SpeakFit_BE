package com.speakfit.backend.global.infra.s3;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** 실제 AWS SDK 서명 코드를 사용한다. 서명은 로컬 계산이라 S3 접속 없이 확인할 수 있다. */
@DisplayName("S3 서명 URL")
class S3PresignTest {

    private static final String BUCKET = "speakfit-prod-1";
    private static final String OWN_URL =
            "https://speakfit-prod-1.s3.ap-northeast-2.amazonaws.com/ppt/10/attempts/a/slides/1.png";

    private S3Service service;

    @BeforeEach
    void setUp() {
        var credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create("AKIAEXAMPLE", "secret"));
        S3Client s3 = S3Client.builder().region(Region.AP_NORTHEAST_2).credentialsProvider(credentials).build();
        S3Presigner presigner = S3Presigner.builder().region(Region.AP_NORTHEAST_2).credentialsProvider(credentials).build();
        service = new S3Service(s3, presigner);
        ReflectionTestUtils.setField(service, "bucket", BUCKET);
        ReflectionTestUtils.setField(service, "presignExpireSeconds", 600L);
    }

    @Test
    @DisplayName("이 버킷의 오브젝트 URL 은 서명과 유효 시간이 붙은 URL 로 바뀐다")
    void signsOwnObjectUrl() {
        String signed = service.presignGet(OWN_URL);

        URI uri = URI.create(signed);
        assertThat(uri.getHost()).isEqualTo("speakfit-prod-1.s3.ap-northeast-2.amazonaws.com");
        assertThat(uri.getPath()).isEqualTo("/ppt/10/attempts/a/slides/1.png");
        assertThat(signed).contains("X-Amz-Signature=").contains("X-Amz-Algorithm=AWS4-HMAC-SHA256");
        assertThat(signed).contains("X-Amz-Expires=600");
    }

    @Test
    @DisplayName("키만 저장된 값(스타일 샘플 음원)도 서명 URL 로 바뀐다")
    void signsPlainKey() {
        String signed = service.presignGet("samples/styles/calm_low_tone_male.mp3");

        assertThat(URI.create(signed).getPath()).isEqualTo("/samples/styles/calm_low_tone_male.mp3");
        assertThat(signed).contains("X-Amz-Signature=");
    }

    @Test
    @DisplayName("경로 스타일 URL 과 인코딩된 키도 처리한다")
    void signsPathStyleAndEncodedKey() {
        String pathStyle = service.presignGet(
                "https://s3.ap-northeast-2.amazonaws.com/speakfit-prod-1/ppt/1/attempts/a/x%20y.pptx");

        assertThat(URI.create(pathStyle).getRawPath()).isEqualTo("/ppt/1/attempts/a/x%20y.pptx");
        assertThat(pathStyle).contains("X-Amz-Signature=");
    }

    @Test
    @DisplayName("null, 빈 값, 다른 곳의 URL 은 그대로 둔다")
    void leavesUnrelatedValuesAlone() {
        assertThat(service.presignGet(null)).isNull();
        assertThat(service.presignGet("")).isEmpty();
        assertThat(service.presignGet("https://cdn.example.com/a.png")).isEqualTo("https://cdn.example.com/a.png");
        assertThat(service.presignGet("https://other-bucket.s3.ap-northeast-2.amazonaws.com/a.png"))
                .isEqualTo("https://other-bucket.s3.ap-northeast-2.amazonaws.com/a.png");
    }

    @Test
    @DisplayName("같은 URL 을 다시 서명해도 키는 이중으로 붙지 않는다")
    void signedUrlKeepsKeyIntact() {
        String signed = service.presignGet(OWN_URL);

        assertThat(URI.create(signed).getPath()).doesNotContain("speakfit-prod-1/");
    }
}

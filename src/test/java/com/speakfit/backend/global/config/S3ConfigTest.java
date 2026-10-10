package com.speakfit.backend.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("S3 자격 증명 선택")
class S3ConfigTest {

    @Test
    @DisplayName("액세스 키와 시크릿 키가 모두 있으면 그 키를 쓴다 (로컬 개발)")
    void usesStaticKeysWhenBothPresent() {
        var provider = S3Config.credentialsProvider("AKIAEXAMPLE", "secret");

        assertThat(provider).isInstanceOf(AWSStaticCredentialsProvider.class);
        assertThat(provider.getCredentials().getAWSAccessKeyId()).isEqualTo("AKIAEXAMPLE");
    }

    @Test
    @DisplayName("키가 비어 있으면 기본 체인(EC2 IAM 역할 등)을 쓴다 (운영)")
    void fallsBackToDefaultChainWhenKeysAreBlank() {
        assertThat(S3Config.credentialsProvider("", "")).isInstanceOf(DefaultAWSCredentialsProviderChain.class);
        assertThat(S3Config.credentialsProvider(null, null)).isInstanceOf(DefaultAWSCredentialsProviderChain.class);
    }

    @Test
    @DisplayName("둘 중 하나만 있으면 불완전한 키를 쓰지 않고 기본 체인으로 간다")
    void partialKeysAreIgnored() {
        assertThat(S3Config.credentialsProvider("AKIAEXAMPLE", "")).isInstanceOf(DefaultAWSCredentialsProviderChain.class);
        assertThat(S3Config.credentialsProvider("", "secret")).isInstanceOf(DefaultAWSCredentialsProviderChain.class);
    }
}

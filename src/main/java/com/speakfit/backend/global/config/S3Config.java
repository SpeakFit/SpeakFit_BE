package com.speakfit.backend.global.config;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class S3Config {

    // 로컬 개발에서만 지정한다. 운영(EC2)에서는 비워 두고 인스턴스에 붙은 IAM 역할을 사용한다.
    @Value("${cloud.aws.credentials.access-key:}")
    private String accessKey;

    @Value("${cloud.aws.credentials.secret-key:}")
    private String secretKey;

    @Value("${cloud.aws.region.static}")
    private String region;

    @Bean
    public AmazonS3 amazonS3() {
        return AmazonS3ClientBuilder
                .standard()
                .withRegion(region)
                .withCredentials(credentialsProvider(accessKey, secretKey))
                .build();
    }

    /**
     * 액세스 키가 둘 다 지정되면 그 키를 쓰고(로컬 개발),
     * 아니면 기본 체인(환경변수, 프로파일, EC2/ECS 의 IAM 역할)으로 자격 증명을 찾는다.
     */
    static AWSCredentialsProvider credentialsProvider(String accessKey, String secretKey) {
        if (StringUtils.hasText(accessKey) && StringUtils.hasText(secretKey)) {
            return new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKey, secretKey));
        }

        return DefaultAWSCredentialsProviderChain.getInstance();
    }
}

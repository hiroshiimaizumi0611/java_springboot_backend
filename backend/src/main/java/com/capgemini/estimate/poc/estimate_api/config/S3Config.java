package com.capgemini.estimate.poc.estimate_api.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * S3アクセスに必要なクライアントを提供する設定クラス。
 * Presigner は署名付きURLの生成に使用します。
 */
@Configuration
public class S3Config {

    private static final Region REGION = Region.AP_NORTHEAST_1;

    /** 資格情報プロバイダ（共通） */
    @Bean
    public AwsCredentialsProvider awsCredentialsProvider() {
        return DefaultCredentialsProvider.builder().build();
    }

    /** S3クライアントのBean定義 */
    @Bean
    @ConditionalOnMissingBean(S3Client.class)
    public S3Client s3Client(AwsCredentialsProvider credentialsProvider) {
        return S3Client.builder()
                .credentialsProvider(credentialsProvider)
                .region(REGION)
                .build();
    }

    /** 署名URL生成専用のS3Presigner Bean定義 */
    @Bean
    @ConditionalOnMissingBean(S3Presigner.class)
    public S3Presigner s3Presigner(AwsCredentialsProvider credentialsProvider) {
        return S3Presigner.builder()
                .credentialsProvider(credentialsProvider)
                .region(REGION)
                .build();
    }
}

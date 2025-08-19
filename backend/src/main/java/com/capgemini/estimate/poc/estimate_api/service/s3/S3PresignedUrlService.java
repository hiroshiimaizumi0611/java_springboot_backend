package com.capgemini.estimate.poc.estimate_api.service.s3;

import java.net.URL;
import java.time.Duration;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * Amazon S3 向けの署名付きURL発行サービス実装。
 *
 * <p>呼び出し時に必ず {@link PresignOptions} でバケット名と有効期限を指定します。
 */
@Service
public class S3PresignedUrlService {

  /** 署名URL生成に使用する S3Presigner クライアント */
  private final S3Presigner presigner;

  /**
   * コンストラクタインジェクション。
   *
   * @param presigner S3Presigner クライアント
   */
  public S3PresignedUrlService(S3Presigner presigner) {
    this.presigner = presigner;
  }

  /** {@inheritDoc} */
  public URL generatePresignedUrl(String objectKey, PresignedUrlOptions options) {
    GetObjectRequest get =
        GetObjectRequest.builder().bucket(options.bucket()).key(objectKey).build();

    GetObjectPresignRequest presign =
        GetObjectPresignRequest.builder()
            .signatureDuration(Duration.ofMinutes(options.expiryMinutes()))
            .getObjectRequest(get)
            .build();

    return presigner.presignGetObject(presign).url();
  }
}

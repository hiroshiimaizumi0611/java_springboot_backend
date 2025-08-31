package com.capgemini.estimate.poc.estimate_api.common.s3;

import java.net.URL;
import java.time.Duration;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * Amazon S3 向けの署名付きURLを生成する共通コンポーネント。
 *
 * <p>呼び出し時に必ず {@link PresignedUrlOptions} でバケット名と有効期限を指定します。
 */
@Component
public class S3UrlSigner {

  /** 署名URL生成に使用する S3Presigner クライアント */
  private final S3Presigner presigner;

  /**
   * コンストラクタインジェクション。
   *
   * @param presigner S3Presigner クライアント
   */
  public S3UrlSigner(S3Presigner presigner) {
    this.presigner = presigner;
  }

  /**
   * 指定されたバケットとオブジェクトキーに対する S3 の署名付き GET URL を生成する。
   *
   * <p>有効期限は {@link PresignedUrlOptions#expiryMinutes()} 分です。
   *
   * @param objectKey 署名対象のオブジェクトキー（バケット内のパス）
   * @param options バケット名と有効期限（分）を含むオプション
   * @return 生成された署名付き URL（HTTPS）
   */
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

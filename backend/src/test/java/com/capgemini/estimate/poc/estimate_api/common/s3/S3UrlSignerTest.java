package com.capgemini.estimate.poc.estimate_api.common.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.URL;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

/**
 * {@code S3UrlSigner}の単体テスト。
 *
 * <p>S3Presignerをモックして動作を検証する。
 */
class S3UrlSignerTest {

  /** presignerの呼び出しと戻りURLを検証する。 */
  @Test
  void testGeneratePresignedUrl_UsesBucketKeyAndExpiryAndReturnsUrl() throws Exception {
    // モックと戻り値のスタブ
    S3Presigner presigner = mock(S3Presigner.class);
    PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
    URL expected =
        URI.create("https://test-bucket.s3.ap-northeast-1.amazonaws.com/path/to/file.txt").toURL();
    when(presigned.url()).thenReturn(expected);
    when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);

    // 実行対象
    S3UrlSigner signer = new S3UrlSigner(presigner);
    String bucket = "my-test-bucket";
    String key = "path/to/file.txt";
    PresignedUrlOptions options = PresignedUrlOptions.of(bucket, 10);

    URL actual = signer.generatePresignedUrl(key, options);

    // 返却URLの検証
    assertThat(actual).isEqualTo(expected);
  }
}

package com.capgemini.estimate.poc.estimate_api.common.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** {@code PresignedUrlOptions} のバリデーションテスト。 */
class PresignedUrlOptionsTest {

  /** バケット名は空白・null を許容しない。 */
  @Test
  void bucket_mustNotBeBlank() {
    assertThrows(IllegalArgumentException.class, () -> PresignedUrlOptions.of("", 5));
    assertThrows(IllegalArgumentException.class, () -> PresignedUrlOptions.of(" \t\n", 5));
    assertThrows(IllegalArgumentException.class, () -> new PresignedUrlOptions(null, 5));
  }

  /** 有効期限は 1〜15 分の範囲外で例外。 */
  @Test
  void expiry_outOfRange_throws() {
    assertThrows(IllegalArgumentException.class, () -> PresignedUrlOptions.of("b", 0));
    assertThrows(IllegalArgumentException.class, () -> PresignedUrlOptions.of("b", -1));
    assertThrows(IllegalArgumentException.class, () -> PresignedUrlOptions.of("b", 16));
  }

  /** 1分および15分は許容される。 */
  @Test
  void expiry_boundary_ok() {
    PresignedUrlOptions o1 = PresignedUrlOptions.of("bucket", 1);
    PresignedUrlOptions o2 = PresignedUrlOptions.of("bucket", 15);
    assertThat(o1.bucket()).isEqualTo("bucket");
    assertThat(o1.expiryMinutes()).isEqualTo(1);
    assertThat(o2.expiryMinutes()).isEqualTo(15);
  }
}

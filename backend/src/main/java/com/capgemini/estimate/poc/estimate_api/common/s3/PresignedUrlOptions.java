package com.capgemini.estimate.poc.estimate_api.common.s3;

/**
 * 署名付きURL発行時のオプション設定クラス。
 *
 * <p>バケット名と有効期限（分）を明示的に指定します。 有効期限は 1〜15 分の範囲で指定する必要があります。
 */
public record PresignedUrlOptions(String bucket, long expiryMinutes) {

  public PresignedUrlOptions {
    if (bucket == null || bucket.isBlank()) {
      throw new IllegalArgumentException("バケット名が指定されていません。null または空の値は使用できません。");
    }
    if (expiryMinutes <= 0 || expiryMinutes > 15) {
      throw new IllegalArgumentException("有効期限（分）は 1 以上 15 以下で指定してください。");
    }
  }

  /**
   * 静的ファクトリメソッド。
   *
   * @param bucket バケット名（null または空は不可）
   * @param expiryMinutes 有効期限（分）（1〜15分）
   * @return {@link PresignedUrlOptions} のインスタンス
   */
  public static PresignedUrlOptions of(String bucket, long expiryMinutes) {
    return new PresignedUrlOptions(bucket, expiryMinutes);
  }
}

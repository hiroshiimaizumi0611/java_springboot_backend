package com.capgemini.estimate.poc.estimate_api.service;

/**
 * 署名付きURL発行時のオプション設定クラス。
 *
 * <p>バケット名と有効期限（分）を明示的に指定します。 有効期限は 1〜15 分の範囲で指定する必要があります。
 */
public class PresignOptions {

  /** バケット名（必須） */
  private final String bucket;

  /** 有効期限（分）（必須、1〜15分以内） */
  private final long expiryMinutes;

  /**
   * プライベートコンストラクタ。 インスタンス生成は {@link #of(String, long)} を使用してください。
   *
   * @param bucket バケット名（null または空は不可）
   * @param expiryMinutes 有効期限（分）（1〜15分）
   */
  private PresignOptions(String bucket, long expiryMinutes) {
    if (bucket == null || bucket.isBlank()) {
      throw new IllegalArgumentException("Bucket name must not be null or empty.");
    }
    if (expiryMinutes <= 0 || expiryMinutes > 15) {
      throw new IllegalArgumentException("Expiry minutes must be between 1 and 15.");
    }
    this.bucket = bucket;
    this.expiryMinutes = expiryMinutes;
  }

  /**
   * 静的ファクトリメソッド。
   *
   * @param bucket バケット名（null または空は不可）
   * @param expiryMinutes 有効期限（分）（1〜15分）
   * @return {@link PresignOptions} のインスタンス
   */
  public static PresignOptions of(String bucket, long expiryMinutes) {
    return new PresignOptions(bucket, expiryMinutes);
  }

  /** バケット名を返します。 */
  public String bucket() {
    return bucket;
  }

  /** 有効期限（分）を返します。 */
  public long expiryMinutes() {
    return expiryMinutes;
  }
}

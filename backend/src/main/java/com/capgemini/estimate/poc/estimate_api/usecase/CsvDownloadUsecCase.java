package com.capgemini.estimate.poc.estimate_api.usecase;

import java.net.URL;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Service
public class CsvDownloadUsecCase {

  private final S3Presigner presigner;
  private final String bucketName;
  private final Duration expiry;

  public CsvDownloadUsecCase(
      S3Presigner presigner,
      @Value("${app.csv.bucket-name}") String bucketName,
      @Value("${app.csv.url-expiry-minutes:10}") long expiryMinutes) {
    this.presigner = presigner;
    this.bucketName = bucketName;
    this.expiry = Duration.ofMinutes(expiryMinutes);
  }

  public URL generatePresignedUrl(String objectKey) {
    GetObjectRequest get =
        GetObjectRequest.builder()
            .bucket(bucketName)
            .key(objectKey)
            // ダウンロードさせたい場合はヘッダを付与
            .responseContentDisposition("attachment; filename=\"" + objectKey + "\"")
            .build();

    GetObjectPresignRequest presign =
        GetObjectPresignRequest.builder().signatureDuration(expiry).getObjectRequest(get).build();

    AwsCredentials creds = DefaultCredentialsProvider.create().resolveCredentials();
    System.out.println("AccessKeyId:" + creds.accessKeyId()); 

    return presigner.presignGetObject(presign).url();
  }
}

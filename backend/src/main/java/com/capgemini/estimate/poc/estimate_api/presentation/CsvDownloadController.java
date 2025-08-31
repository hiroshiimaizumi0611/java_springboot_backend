package com.capgemini.estimate.poc.estimate_api.presentation;

import java.net.URL;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.capgemini.estimate.poc.estimate_api.common.s3.PresignedUrlOptions;
import com.capgemini.estimate.poc.estimate_api.common.s3.S3UrlSigner;


@RestController
@RequestMapping("/api/csv")
public class CsvDownloadController {
  private final S3UrlSigner s3UrlSigner;

  public CsvDownloadController(S3UrlSigner s3UrlSigner) {
    this.s3UrlSigner = s3UrlSigner;
  }

  /** GET /api/v1/csv/{fileName} → { "url": "https://signed-url..." } */
  @GetMapping("/{fileName}")
  public ResponseEntity<Map<String, String>> getDownloadUrl(@PathVariable String fileName) {
    URL url =
        s3UrlSigner.generatePresignedUrl(
            fileName, PresignedUrlOptions.of("estimate-app-csv-files", 15));
    return ResponseEntity.ok(Map.of("url", url.toString()));
  }
}

package com.capgemini.estimate.poc.estimate_api.presentation;

import com.capgemini.estimate.poc.estimate_api.service.PresignOptions;
import com.capgemini.estimate.poc.estimate_api.service.S3PresignedUrlService;
import java.net.URL;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/csv")
public class CsvDownloadController {
  private final S3PresignedUrlService s3PresignedUrlService;

  public CsvDownloadController(S3PresignedUrlService s3PresignedUrlService) {
    this.s3PresignedUrlService = s3PresignedUrlService;
  }

  /** GET /api/v1/csv/{fileName} → { "url": "https://signed-url..." } */
  @GetMapping("/{fileName}")
  public ResponseEntity<Map<String, String>> getDownloadUrl(@PathVariable String fileName) {
    URL url =
        s3PresignedUrlService.generatePresignedUrl(
            fileName, PresignOptions.of("estimate-app-csv-files", 15));
    return ResponseEntity.ok(Map.of("url", url.toString()));
  }
}

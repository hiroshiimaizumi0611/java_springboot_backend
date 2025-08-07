package com.capgemini.estimate.poc.estimate_api.presentation;

import com.capgemini.estimate.poc.estimate_api.usecase.CsvDownloadUsecCase;
import java.net.URL;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/csv")
public class CsvDownloadController {
  private final CsvDownloadUsecCase csvDownloadUsecCase;

  public CsvDownloadController(CsvDownloadUsecCase csvDownloadUsecCase) {
    this.csvDownloadUsecCase = csvDownloadUsecCase;
  }

  /** GET /api/v1/csv/{fileName} → { "url": "https://signed-url..." } */
  @GetMapping("/{fileName}")
  public ResponseEntity<Map<String, String>> getDownloadUrl(@PathVariable String fileName) {
    URL url = csvDownloadUsecCase.generatePresignedUrl(fileName);
    return ResponseEntity.ok(Map.of("url", url.toString()));
  }
}

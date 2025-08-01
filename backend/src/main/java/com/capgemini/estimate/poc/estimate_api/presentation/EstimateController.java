package com.capgemini.estimate.poc.estimate_api.presentation;

import com.capgemini.estimate.poc.estimate_api.domain.model.Estimate;
import com.capgemini.estimate.poc.estimate_api.usecase.DownloadEstimateExcelUseCase;
import com.capgemini.estimate.poc.estimate_api.usecase.EstimateUseCase;
import java.util.List;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/estimates")
public class EstimateController {

  private final DownloadEstimateExcelUseCase downloadUseCase;
  private final EstimateUseCase estimateUseCase;

  public EstimateController(
      DownloadEstimateExcelUseCase downloadUseCase, EstimateUseCase estimateUseCase) {
    this.downloadUseCase = downloadUseCase;
    this.estimateUseCase = estimateUseCase;
  }

  @GetMapping("/download")
  public ResponseEntity<byte[]> downloadExcel() {
    byte[] file = downloadUseCase.execute();
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=estimates.xlsx")
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .body(file);
  }

  @GetMapping
  public List<Estimate> getEstimates() {
    return estimateUseCase.getAllEstimates();
  }

  @GetMapping("/{id}")
  public Estimate getEstimate(@PathVariable String id) {
    return estimateUseCase.getEstimate(id);
  }

  @PostMapping
  public void addEstimate(@RequestBody Estimate estimate) {
    estimateUseCase.insertEstimate(estimate);
  }

  @DeleteMapping("/{id}")
  public void deleteEstimate(@PathVariable String id) {
    estimateUseCase.deleteEstimate(id);
  }

  @PutMapping("/{id}")
  public void deleteEstimate(@PathVariable String id, @RequestBody Estimate estimate) {
    estimateUseCase.updateEstimate(estimate);
  }
}

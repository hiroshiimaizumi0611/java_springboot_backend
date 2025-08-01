package com.capgemini.estimate.poc.estimate_api.usecase;

import com.capgemini.estimate.poc.estimate_api.domain.model.Estimate;
import com.capgemini.estimate.poc.estimate_api.domain.repository.EstimateRepository;
import java.io.ByteArrayOutputStream;
import java.util.List;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DownloadEstimateExcelUseCase {

  @Autowired private final EstimateRepository repository;

  public DownloadEstimateExcelUseCase(EstimateRepository repository) {
    this.repository = repository;
  }

  public byte[] execute() {
    List<Estimate> estimates = repository.selectAll();

    try (Workbook workbook = new XSSFWorkbook();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {

      Sheet sheet = workbook.createSheet("Estimates");

      Row header = sheet.createRow(0);
      header.createCell(0).setCellValue("見積ID");
      header.createCell(1).setCellValue("タイトル");
      header.createCell(2).setCellValue("顧客名");
      header.createCell(3).setCellValue("合計金額");

      int index = 1;
      for (Estimate e : estimates) {
        Row row = sheet.createRow(index++);
        row.createCell(0).setCellValue(e.id);
        row.createCell(1).setCellValue(e.title);
        row.createCell(2).setCellValue(e.customerName);
        row.createCell(3).setCellValue(e.totalAmount);
      }

      workbook.write(out);
      return out.toByteArray();

    } catch (Exception e) {
      throw new RuntimeException("Excel出力失敗", e);
    }
  }
}

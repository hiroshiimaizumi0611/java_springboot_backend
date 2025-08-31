package com.capgemini.estimate.poc.estimate_api.presentation;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 開発/ローカル検証用：指定秒数スリープするエンドポイント。
 * <p>
 * - パス: GET /api/test/sleep?seconds=30
 * - プロファイル: local, dev のみ有効
 * - 用途: グレースフルシャットダウン検証時に長時間処理を模擬
 */
@RestController
@RequestMapping("/api/test")
public class DevSleepController {

  private static final Logger log = LoggerFactory.getLogger(DevSleepController.class);

  /**
   * 指定秒スリープする。最大300秒までに制限。
   */
  @GetMapping("/sleep")
  public Map<String, Object> sleep(@RequestParam(name = "seconds", defaultValue = "30") int seconds)
      throws InterruptedException {
    int capped = Math.max(0, Math.min(seconds, 300));
    long start = System.currentTimeMillis();
    log.info("/api/test/sleep start: seconds={} thread={}", capped, Thread.currentThread().getName());
    try {
      Thread.sleep(capped * 1000L);
    } finally {
      long end = System.currentTimeMillis();
      log.info("/api/test/sleep end:   seconds={} elapsedMs={} thread={}", capped, (end - start), Thread.currentThread().getName());
    }
    Map<String, Object> body = new HashMap<>();
    body.put("status", "ok");
    body.put("sleptSeconds", capped);
    body.put("startedAt", Instant.ofEpochMilli(start).toString());
    body.put("finishedAt", Instant.ofEpochMilli(System.currentTimeMillis()).toString());
    body.put("thread", Thread.currentThread().getName());
    return body;
  }
}

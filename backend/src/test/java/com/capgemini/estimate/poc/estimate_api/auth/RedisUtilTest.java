package com.capgemini.estimate.poc.estimate_api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;

/** {@code RedisUtil} の単体テスト。RedisTemplate をモックして振る舞いを検証する。 */
@ExtendWith(MockitoExtension.class)
class RedisUtilTest {

  @Mock private RedisTemplate<String, String> redis;
  @Mock private HashOperations<String, Object, Object> hashOps;

  private RedisUtil util;

  @BeforeEach
  void setUp() {
    // 共通のスタブ設定（HashOperations はすべてのテストで使用）
    when(redis.opsForHash()).thenReturn(hashOps);
    util = new RedisUtil(redis);
  }

  /**
   * ログイン処理の upsert で、端末セッション Hash に userId/ver/lastSeen を保存し、
   * TTL(14日) を設定することを検証する。
   */
  @Test
  void upsertOnLogin_putsHash_andSetsTtl() {
    // 準備
    String userId = "u1";
    String sid = "s1";
    // 実行
    util.upsertOnLogin(userId, sid, 1L);

    // Hash に userId / ver / lastSeen が保存される（Map の中身を直接マッチ）
    verify(hashOps).putAll(
        eq("sess:" + sid),
        argThat((Map<?, ?> m) ->
            userId.equals(m.get("userId"))
                && "1".equals(m.get("ver"))
                && String.valueOf(m.get("lastSeen")).matches("\\d+")
        )
    );

    // TTL が 14 日に設定される
    verify(redis).expire("sess:" + sid, Duration.ofDays(14));
  }

  /**
   * incrementVer: ver が存在しない場合に 1 をセットし、lastSeen 更新と TTL 延長が行われること。
   */
  @Test
  void incrementVer_whenMissing_setsOne_andUpdatesLastSeen() {
    // ver が存在しない場合は 1 をセット
    String sid = "s2";
    String key = "sess:" + sid;
    when(hashOps.get(key, "ver")).thenReturn(null);

    util.incrementVer(sid);

    // ver=1 に更新し、lastSeen を現在時刻に更新（数字文字列）、TTL を延長
    verify(hashOps).put(key, "ver", "1");
    verify(hashOps).put(eq(key), eq("lastSeen"), argThat(v -> v != null && v.toString().matches("\\d+")));
    verify(redis).expire(key, Duration.ofDays(14));
  }

  /**
   * incrementVer: 既存の ver を +1 し、lastSeen 更新と TTL 延長が行われること。
   */
  @Test
  void incrementVer_whenPresent_increments_andUpdatesLastSeen() {
    // 既存の ver を +1（5 -> 6）
    String sid = "s3";
    String key = "sess:" + sid;
    when(hashOps.get(key, "ver")).thenReturn("5");

    util.incrementVer(sid);

    // ver を 6 にし、lastSeen を更新（数字文字列）、TTL を延長
    verify(hashOps).put(key, "ver", "6");
    verify(hashOps).put(eq(key), eq("lastSeen"), argThat(v -> v != null && v.toString().matches("\\d+")));
    verify(redis).expire(key, Duration.ofDays(14));
  }

  /**
   * validateAccessAndTouch: Redis に ver が無い場合は認可 NG(false) となり、touch されないこと。
   */
  @Test
  void validateAccessAndTouch_nullVer_returnsFalse() {
    // ver が存在しない場合は認可 NG（touch もしない）
    String sid = "s4";
    String key = "sess:" + sid;
    when(hashOps.get(key, "ver")).thenReturn(null);

    boolean ok = util.validateAccessAndTouch(sid, 1L, 5L);
    assertThat(ok).isFalse();
    // lastSeen の更新や TTL 延長は呼ばれない
    verify(hashOps, never()).put(any(), eq("lastSeen"), anyString());
    verify(redis, never()).expire(anyString(), any(Duration.class));
  }

  /**
   * validateAccessAndTouch: AT の ver と Redis の ver が不一致なら NG(false) で、touch されないこと。
   */
  @Test
  void validateAccessAndTouch_versionMismatch_returnsFalse() {
    // ver 不一致は認可 NG（touch なし）
    String sid = "s5";
    String key = "sess:" + sid;
    when(hashOps.get(key, "ver")).thenReturn("2");

    boolean ok = util.validateAccessAndTouch(sid, 1L, 5L);
    assertThat(ok).isFalse();
    verify(hashOps, never()).put(any(), eq("lastSeen"), anyString());
    verify(redis, never()).expire(anyString(), any(Duration.class));
  }

  /**
   * validateAccessAndTouch: 無操作タイムアウトを超過していれば NG(false) で、touch されないこと。
   */
  @Test
  void validateAccessAndTouch_idleTimeoutExceeded_returnsFalse() {
    // 無操作タイムアウト超過は認可 NG（touch なし）
    String sid = "s6";
    String key = "sess:" + sid;
    long idleMin = 1L;
    long idleSec = idleMin * 60;
    long lastSeen = Instant.now().getEpochSecond() - idleSec - 1; // 1秒超過

    when(hashOps.get(key, "ver")).thenReturn("3");
    when(hashOps.get(key, "lastSeen")).thenReturn(String.valueOf(lastSeen));

    boolean ok = util.validateAccessAndTouch(sid, 3L, idleMin);
    assertThat(ok).isFalse();
    verify(hashOps, never()).put(any(), eq("lastSeen"), anyString());
    verify(redis, never()).expire(anyString(), any(Duration.class));
  }

  /**
   * validateAccessAndTouch: ver 一致かつタイムアウト内なら OK(true)。lastSeen 更新と TTL 延長が行われること。
   */
  @Test
  void validateAccessAndTouch_ok_updatesLastSeen_andReturnsTrue() {
    // ver 一致かつ無操作タイムアウト内は認可 OK（touch あり）
    String sid = "s7";
    String key = "sess:" + sid;
    long idleMin = 5L;
    long idleSec = idleMin * 60;
    long lastSeen = Instant.now().getEpochSecond() - idleSec + 1; // 許容内

    when(hashOps.get(key, "ver")).thenReturn("7");
    when(hashOps.get(key, "lastSeen")).thenReturn(String.valueOf(lastSeen));

    boolean ok = util.validateAccessAndTouch(sid, 7L, idleMin);
    assertThat(ok).isTrue();

    // lastSeen を更新（数字文字列）、TTL を延長
    verify(hashOps).put(eq(key), eq("lastSeen"), argThat(v -> v != null && v.toString().matches("\\d+")));
    verify(redis).expire(key, Duration.ofDays(14));
  }

  /**
   * getVer: 数値なら値を返し、非数値や未設定時は null を返すこと。
   */
  @Test
  void getVer_numericAndInvalid_returnsExpected() {
    // 数値はパースして返却、非数値や null は null
    String key = "sess:gx";

    when(hashOps.get(key, "ver")).thenReturn("10");
    assertThat(util.getVer("gx")).isEqualTo(10L);

    when(hashOps.get(key, "ver")).thenReturn("abc");
    assertThat(util.getVer("gx")).isNull();

    when(hashOps.get(key, "ver")).thenReturn(null);
    assertThat(util.getVer("gx")).isNull();
  }
}

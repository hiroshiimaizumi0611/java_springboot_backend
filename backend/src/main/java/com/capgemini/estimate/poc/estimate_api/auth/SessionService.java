package com.capgemini.estimate.poc.estimate_api.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * セッションメタ情報（sid, ver, lastSeen, userId）を Redis に保存・検証するサービス。
 * <p>
 * キー構造:
 * - セッションメタ: {@code sess:{sid}}（Hash）
 * - ユーザー索引: {@code user:{userId}:sids}（Set）
 * TTL は 14 日（RT の有効期限と同程度）に設定。
 */
@Service
public class SessionService {

  private final RedisTemplate<String, String> redis;
  private static final Duration TTL = Duration.ofDays(14);

  public SessionService(RedisTemplate<String, String> redis) {
    this.redis = redis;
  }

  /** セッションメタの Redis キー（sess:{sid}）を返す。 */
  public String key(String sid) {
    return "sess:" + sid;
  }

  /** ユーザー索引キー（user:{userId}:sids）を返す。 */
  public String userIndexKey(String userId) {
    return "user:" + userId + ":sids";
  }

  /** ログイン時にセッションメタを作成（RT jti は扱わない簡素版）。 */
  public void upsertOnLogin(String userId, String sid, long ver) {
    String k = key(sid);
    Map<String, String> map = new HashMap<>();
    map.put("userId", userId);
    map.put("ver", String.valueOf(ver));
    map.put("lastSeen", String.valueOf(Instant.now().getEpochSecond()));
    redis.opsForHash().putAll(k, map);
    redis.expire(k, TTL);
    redis.opsForSet().add(userIndexKey(userId), sid);
  }

  /** ver を 1 増加（失効）させ、lastSeen/TTL を更新する。 */
  public void incrementVer(String sid) {
    String k = key(sid);
    Object cur = redis.opsForHash().get(k, "ver");
    long next = (cur == null ? 1 : Long.parseLong(cur.toString()) + 1);
    redis.opsForHash().put(k, "ver", String.valueOf(next));
    touch(sid);
  }

  /** lastSeen を現在時刻に更新し、TTL を延長する。 */
  public void touch(String sid) {
    String k = key(sid);
    redis.opsForHash().put(k, "lastSeen", String.valueOf(Instant.now().getEpochSecond()));
    redis.expire(k, TTL);
  }

  /** 現在のセッションバージョン（ver）を取得する。存在しなければ null。 */
  public Long getVer(String sid) {
    String k = key(sid);
    Object v = redis.opsForHash().get(k, "ver");
    if (v == null) return null;
    try {
      return Long.parseLong(String.valueOf(v));
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /**
   * アクセス（AT）時の照合: ver 一致かつ無操作タイムアウト未超過なら lastSeen 更新して true。
   * 条件を満たさなければ false。
   */
  public boolean validateAccessAndTouch(String sid, long ver, long idleTimeoutMinutes) {
    String k = key(sid);
    Object curVer = redis.opsForHash().get(k, "ver");
    Object lastSeen = redis.opsForHash().get(k, "lastSeen");
    if (curVer == null) return false;
    boolean verOk = String.valueOf(ver).equals(String.valueOf(curVer));
    if (!verOk) return false;
    long now = Instant.now().getEpochSecond();
    long last = lastSeen == null ? now : Long.parseLong(lastSeen.toString());
    long idleSec = idleTimeoutMinutes * 60;
    if (now - last > idleSec) {
      return false;
    }
    touch(sid);
    return true;
  }
}

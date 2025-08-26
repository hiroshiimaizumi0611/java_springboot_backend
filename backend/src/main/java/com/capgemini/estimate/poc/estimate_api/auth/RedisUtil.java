package com.capgemini.estimate.poc.estimate_api.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Redis を用いた端末セッション情報（sid, ver, lastSeen, userId）のヘルパ。
 * <p>
 * キー構造:
 * - 端末セッション情報: {@code sess:{sid}}（Hash）
 * - ユーザー索引: {@code user:{userId}:sids}（Set）
 * 有効期限（TTL）は最終アクセスから14日（スライディング）。
 */
@Service
public class RedisUtil {

  private final RedisTemplate<String, String> redisTemplate;
  private static final Duration SESSION_META_TTL = Duration.ofDays(14);

  public RedisUtil(RedisTemplate<String, String> redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  /**
   * 端末セッション情報の Redis キー（sess:{sid}）を返す。
   *
   * @param sid 端末セッションID
   * @return Redis キー文字列
   */
  public String sessionKey(String sid) {
    return "sess:" + sid;
  }

  /**
   * ユーザー索引キー（user:{userId}:sids）を返す。
   *
   * @param userId ユーザーID
   * @return Redis キー文字列
   */
  public String userIndexKey(String userId) {
    return "user:" + userId + ":sids";
  }

  /**
   * ログイン時に端末セッション情報を作成（または更新）する。
   *
   * @param userId ユーザーID
   * @param sid 端末セッションID
   * @param sessionVersion セッションバージョン（初期値=1）
   */
  public void upsertOnLogin(String userId, String sid, long sessionVersion) {
    String key = sessionKey(sid);
    Map<String, String> values = new HashMap<>();
    values.put("userId", userId);
    values.put("ver", String.valueOf(sessionVersion));
    values.put("lastSeen", String.valueOf(Instant.now().getEpochSecond()));
    redisTemplate.opsForHash().putAll(key, values);
    redisTemplate.expire(key, SESSION_META_TTL);
    redisTemplate.opsForSet().add(userIndexKey(userId), sid);
  }

  /**
   * セッションバージョンを1増やし（失効）、最終アクセス時刻とTTLを更新する。
   *
   * @param sid 端末セッションID
   */
  public void incrementVer(String sid) {
    String key = sessionKey(sid);
    Object current = redisTemplate.opsForHash().get(key, "ver");
    long nextVersion = (current == null ? 1 : Long.parseLong(current.toString()) + 1);
    redisTemplate.opsForHash().put(key, "ver", String.valueOf(nextVersion));
    updateLastSeen(sid);
  }

  /**
   * 最終アクセス時刻（lastSeen）を現在時刻に更新し、TTL を延長する。
   *
   * @param sid 端末セッションID
   */
  public void updateLastSeen(String sid) {
    String key = sessionKey(sid);
    redisTemplate.opsForHash().put(key, "lastSeen", String.valueOf(Instant.now().getEpochSecond()));
    redisTemplate.expire(key, SESSION_META_TTL);
  }

  /**
   * アクセス時の照合: ver が一致し、かつ無操作タイムアウト未超過であれば lastSeen を更新して true を返す。
   * いずれかに該当しない場合は false。
   *
   * @param sid 端末セッションID
   * @param sessionVersion AT に含まれるセッションバージョン
   * @param idleTimeoutMinutes 無操作タイムアウト（分）
   * @return 照合OKなら true、NG なら false
   */
  public boolean validateAccessAndTouch(String sid, long sessionVersion, long idleTimeoutMinutes) {
    String key = sessionKey(sid);
    Object storedVer = redisTemplate.opsForHash().get(key, "ver");
    Object storedLastSeen = redisTemplate.opsForHash().get(key, "lastSeen");
    if (storedVer == null) {
      return false;
    }
    boolean versionMatches = String.valueOf(sessionVersion).equals(String.valueOf(storedVer));
    if (!versionMatches) {
      return false;
    }
    long nowEpoch = Instant.now().getEpochSecond();
    long lastSeenEpoch = (storedLastSeen == null ? nowEpoch : Long.parseLong(storedLastSeen.toString()));
    long idleSeconds = idleTimeoutMinutes * 60;
    if (nowEpoch - lastSeenEpoch > idleSeconds) {
      return false;
    }
    updateLastSeen(sid);
    return true;
  }

  /**
   * 現在のセッションバージョン（ver）を取得する。
   *
   * @param sid 端末セッションID
   * @return ver 値。存在しない場合は null。
   */
  public Long getVer(String sid) {
    String key = sessionKey(sid);
    Object value = redisTemplate.opsForHash().get(key, "ver");
    if (value == null) {
      return null;
    }
    try {
      return Long.parseLong(String.valueOf(value));
    } catch (NumberFormatException e) {
      return null;
    }
  }
}

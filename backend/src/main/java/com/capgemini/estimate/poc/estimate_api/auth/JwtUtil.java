package com.capgemini.estimate.poc.estimate_api.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * アプリケーション発行の JWT（AT）を扱うユーティリティ。
 * <p>
 * - 署名: HS256（共有シークレット {@code jwt.secret}）。
 * - クレーム: sub, exp, sid, ver（最小限）
 */
@Service
public class JwtUtil {

  @Value("${jwt.secret}")
  private String secret;

  /** HS256 の署名鍵を生成する。シークレットは十分な長さ（32バイト以上）を推奨。 */
  private SecretKey key() {
    byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
    return Keys.hmacShaKeyFor(bytes);
  }

  /**
   * AT（短命）を生成する。sid/ver を含め、端末セッション情報と整合性を取る。
   *
   * @param subject JWT の subject（ユーザー識別子等）
   * @param sid 端末セッションID
   * @param sessionVersion セッションバージョン（ver）
   * @param ttlSeconds 有効期限（秒）
   * @return 署名済み JWT（AT）
   */
  public String createAccessToken(String subject, String sid, long sessionVersion, long ttlSeconds) {
    Instant now = Instant.now();
    
    return Jwts.builder()
        .subject(subject)
        .expiration(Date.from(now.plusSeconds(ttlSeconds)))
        .claim("sid", sid)
        .claim("ver", sessionVersion)
        .signWith(key(), Jwts.SIG.HS256)
        .compact();
  }

  /**
   * 署名検証済みの主要クレームを取り出す。
   *
   * @param jwt 検証対象の JWT
   * @return クレームのマップ
   * @throws io.jsonwebtoken.JwtException 署名不正や期限切れ等で検証に失敗した場合
   */
  public Map<String, Object> parseClaims(String jwt) {
    return Jwts.parser().verifyWith(key()).build().parseSignedClaims(jwt).getPayload();
  }
}

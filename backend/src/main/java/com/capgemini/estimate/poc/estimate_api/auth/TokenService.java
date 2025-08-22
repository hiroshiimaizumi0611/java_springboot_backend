package com.capgemini.estimate.poc.estimate_api.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 自前 JWT（AT）の生成・検証ユーティリティ。
 * <p>
 * - アルゴリズム: HS256（共有シークレット）。環境変数 {@code APP_JWT_SECRET} を利用。
 * - クレーム: iss, aud(=iss), iat, nbf, exp, jti, sid, ver
 */
@Service
public class TokenService {

  @Value("${jwt.secret}")
  private String secret;

  @Value("${spring.application.name:estimate-api}")
  private String issuer;

  /** HS256 の署名鍵を生成。シークレットは十分な長さ（32バイト以上）を推奨。 */
  private SecretKey  key() {
    byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
    return Keys.hmacShaKeyFor(bytes);
  }

  /** AT（短命）を生成。sid/ver を含め、サーバ側セッションと整合性を取る。 */
  public String createAccessToken(String subject, String sid, long sessionVersion, long ttlSeconds) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(subject)
        .issuer(issuer)
        .audience().add(issuer).and()
        .issuedAt(Date.from(now))
        .notBefore(Date.from(now))
        .expiration(Date.from(now.plusSeconds(ttlSeconds)))
        .id(UUID.randomUUID().toString())
        .claim("sid", sid)
        .claim("ver", sessionVersion)
        .signWith(key(), Jwts.SIG.HS256)
        .compact();
  }

  /** 署名検証済みのクレームを取り出す（失敗時は例外）。 */
  public Map<String, Object> parseClaims(String jwt) {
    return Jwts.parser().verifyWith(key()).build().parseSignedClaims(jwt).getPayload();
  }
}

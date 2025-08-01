package com.capgemini.estimate.poc.estimate_api.auth;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtUtil {
  @Value("${jwt.secret}")
  private String secret;

  private final long expiration = 900_000; // 15min

  public String createToken(String username) {
    var key = Keys.hmacShaKeyFor(secret.getBytes());
    return Jwts.builder()
        .subject(username)
        .expiration(new Date(System.currentTimeMillis() + expiration))
        .signWith(key)
        .compact();
  }

  public String getUsername(String token) {
    var key = Keys.hmacShaKeyFor(secret.getBytes());
    return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject();
  }

  public boolean validateToken(String token) {
    var key = Keys.hmacShaKeyFor(secret.getBytes());
    try {
      Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
      return true;
    } catch (JwtException e) {
      return false;
    }
  }

  public long getRemainingExpiration(String token) {
    var key = Keys.hmacShaKeyFor(secret.getBytes());
    Date expiration =
        Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getExpiration();
    long now = System.currentTimeMillis();
    long remain = (expiration.getTime() - now) / 1000;

    return Math.max(remain, 1);
  }
}

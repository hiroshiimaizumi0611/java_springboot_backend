package com.capgemini.estimate.poc.estimate_api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.jsonwebtoken.JwtException;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** {@code JwtUtil} の単体テスト。 */
class JwtUtilTest {

  private JwtUtil jwtUtilWithSecret(String secret) {
    JwtUtil util = new JwtUtil();
    ReflectionTestUtils.setField(util, "secret", secret);
    return util;
  }

  /** 正常系: 生成したトークンを同一シークレットで検証し、主要クレームを確認。 */
  @Test
  void createAndParse_validToken_returnsExpectedClaims() {
    String secret = "this_is_a_very_long_random_secret_key_32byte!"; // 32 bytes
    JwtUtil util = jwtUtilWithSecret(secret);

    String subject = "example_subject";
    String sid = "sid";
    long ver = 5L;
    long ttl = 600L; // 10 minutes
    Instant before = Instant.now();

    String jwt = util.createAccessToken(subject, sid, ver, ttl);
    Map<String, Object> claims = util.parseClaims(jwt);

    // subject, sid, ver を確認
    assertThat(claims.get("sub")).isEqualTo(subject);
    assertThat(claims.get("sid")).isEqualTo(sid);
    assertThat(((Number) claims.get("ver")).longValue()).isEqualTo(ver);

    Date exp = ((io.jsonwebtoken.Claims) claims).getExpiration();
    assertThat(exp.toInstant()).isAfter(before);
  }

  /** 期限切れトークンは JwtException を投げる。 */
  @Test
  void parseClaims_expiredToken_throws() {
    String secret = "this_is_a_very_long_random_secret_key_32byte!";
    JwtUtil util = jwtUtilWithSecret(secret);

    String jwt = util.createAccessToken("user", "sid", 1L, -10L); // 10秒過去

    assertThrows(JwtException.class, () -> util.parseClaims(jwt));
  }

  /** シークレットが異なると署名検証に失敗し JwtException。 */
  @Test
  void parseClaims_invalidSignature_throws() {
    JwtUtil issuer = jwtUtilWithSecret("this_is_a_very_long_random_secret_key_32byte!");
    JwtUtil verifier = jwtUtilWithSecret("this_is_a_very_long_random_secret_key_32byte??");

    String jwt = issuer.createAccessToken("user", "sid", 2L, 60L);

    assertThrows(JwtException.class, () -> verifier.parseClaims(jwt));
  }
}

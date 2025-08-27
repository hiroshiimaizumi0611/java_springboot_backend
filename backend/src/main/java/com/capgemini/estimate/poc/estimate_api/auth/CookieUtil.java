package com.capgemini.estimate.poc.estimate_api.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

/**
 * 認証・UI 用 Cookie をまとめて扱うユーティリティ。
 * <p>
 * - access_token: アクセス用の短命 JWT。HttpOnly / SameSite=Lax / Path=/ で配布。
 * - user_info: 画面表示用ヒント（Base64URL JSON）。非 HttpOnly / SameSite=Lax / Path=/。
 */
@Service
public class CookieUtil {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final Environment environment;

  public CookieUtil(Environment environment) {
    this.environment = environment;
  }

  /** local 以外のプロファイルでは Secure Cookie を有効にする。 */
  public boolean isSecureCookie() {
    return !environment.acceptsProfiles(Profiles.of("local"));
  }

  /**
   * access_token の Set-Cookie をレスポンスに付与する。
   *
   * @param response HttpServletResponse
   * @param accessToken 発行済み AT（JWT）
   * @param atTtl AT の Max-Age
   * @param secure Secure 属性（prod=true/local=false）
   */
  public void setAuthCookies(
      HttpServletResponse response,
      String accessToken,
      Duration atTtl,
      boolean secure) {
    // access_token は全 API で送信されるよう Path=/、CSRF 対応のため SameSite=Lax
    ResponseCookie at =
        ResponseCookie.from("access_token", accessToken)
            .httpOnly(true)
            .secure(secure)
            .sameSite("Lax")
            .path("/")
            .maxAge(atTtl)
            .build();

    response.addHeader("Set-Cookie", at.toString());
  }

  /**
   * access_token の削除用 Set-Cookie（Max-Age=0）をレスポンスに付与する。
   *
   * @param response HttpServletResponse
   * @param secure Secure 属性（prod=true/local=false）
   */
  public void clearAuthCookies(HttpServletResponse response, boolean secure) {
    ResponseCookie at =
        ResponseCookie.from("access_token", "")
            .httpOnly(true)
            .secure(secure)
            .sameSite("Lax")
            .path("/")
            .maxAge(0)
            .build();
    response.addHeader("Set-Cookie", at.toString());
  }

  /**
   * user_info の削除用 Set-Cookie（Max-Age=0）をレスポンスに付与する。
   *
   * @param response HttpServletResponse
   * @param secure Secure 属性（prod=true/local=false）
   */
  public void clearUiCookies(HttpServletResponse response, boolean secure) {
    ResponseCookie ui =
        ResponseCookie.from("user_info", "")
            .httpOnly(false)
            .secure(secure)
            .sameSite("Lax")
            .path("/")
            .maxAge(0)
            .build();
    response.addHeader("Set-Cookie", ui.toString());
  }

  /**
   * UI 表示用ヒント（uid/exp）を生成し、user_info Cookie を設定する。
   * JSON を Base64URL でエンコードし、max-age は ttl に従う。
   */
  public void setUiCookie(
      HttpServletResponse response,
      String uid,
      boolean secure,
      Duration ttl) {
    try {
      Map<String, Object> ui = new HashMap<>();
      ui.put("uid", uid);
      ui.put("exp", Instant.now().plusSeconds(ttl.toSeconds()).getEpochSecond());
      String payload = Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(objectMapper.writeValueAsString(ui).getBytes());
      ResponseCookie cookie =
          ResponseCookie.from("user_info", payload)
              .httpOnly(false)
              .secure(secure)
              .sameSite("Lax")
              .path("/")
              .maxAge(ttl)
              .build();
      response.addHeader("Set-Cookie", cookie.toString());
    } catch (Exception e) {
      throw new RuntimeException("Failed to build UI cookie payload", e);
    }
  }

}

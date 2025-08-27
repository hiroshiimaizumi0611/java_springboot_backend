package com.capgemini.estimate.poc.estimate_api.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

/**
 * 認証・UI 用 Cookie をまとめて扱うユーティリティ。
 * <p>
 * - AT: アクセス用の短命 JWT。HttpOnly / SameSite=Lax / Path=/ で配布。
 * - UI: 画面表示用ヒント（Base64URL JSON）。非 HttpOnly / SameSite=Lax / Path=/。
 */
@Service
public class CookieUtil {

  private final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * AT の Set-Cookie をレスポンスに付与する。
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
    // AT は全 API で送信されるよう Path=/、CSRF 対応のため SameSite=Lax
    ResponseCookie at =
        ResponseCookie.from("AT", accessToken)
            .httpOnly(true)
            .secure(secure)
            .sameSite("Lax")
            .path("/")
            .maxAge(atTtl)
            .build();

    response.addHeader("Set-Cookie", at.toString());
  }

  /**
   * AT の削除用 Set-Cookie（Max-Age=0）をレスポンスに付与する。
   *
   * @param response HttpServletResponse
   * @param secure Secure 属性（prod=true/local=false）
   */
  public void clearAuthCookies(HttpServletResponse response, boolean secure) {
    ResponseCookie at =
        ResponseCookie.from("AT", "")
            .httpOnly(true)
            .secure(secure)
            .sameSite("Lax")
            .path("/")
            .maxAge(0)
            .build();
    response.addHeader("Set-Cookie", at.toString());
  }

  /**
   * UI の削除用 Set-Cookie（Max-Age=0）をレスポンスに付与する。
   *
   * @param response HttpServletResponse
   * @param secure Secure 属性（prod=true/local=false）
   */
  public void clearUiCookies(HttpServletResponse response, boolean secure) {
    ResponseCookie ui =
        ResponseCookie.from("UI", "")
            .httpOnly(false)
            .secure(secure)
            .sameSite("Lax")
            .path("/")
            .maxAge(0)
            .build();
    response.addHeader("Set-Cookie", ui.toString());
  }

  /**
   * UI 表示用ヒント（uid/name/exp）を生成し、UI Cookie を設定する。
   * JSON を Base64URL でエンコードし、max-age は ttl に従う。
   *
   * @param response HttpServletResponse
   * @param uid ユーザーID（必須）
   * @param displayName 表示名（null 可、null の場合は uid を使用）
   * @param secure Secure 属性（prod=true/local=false）
   * @param ttl UI Cookie の存続期間
   */
  public void setUiCookie(
      HttpServletResponse response,
      String uid,
      String displayName,
      boolean secure,
      Duration ttl) {
    try {
      Map<String, Object> ui = new HashMap<>();
      ui.put("uid", uid);
      ui.put("name", (displayName != null ? displayName : uid));
      ui.put("exp", Instant.now().plusSeconds(ttl.toSeconds()).getEpochSecond());
      String payload = Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(objectMapper.writeValueAsString(ui).getBytes());
      ResponseCookie cookie =
          ResponseCookie.from("UI", payload)
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

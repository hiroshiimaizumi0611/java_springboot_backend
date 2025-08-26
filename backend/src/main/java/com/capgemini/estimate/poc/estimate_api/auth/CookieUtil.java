package com.capgemini.estimate.poc.estimate_api.auth;

import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
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
   * UI ヒント用 Cookie をレスポンスに付与する。
   *
   * @param response HttpServletResponse
   * @param base64UrlPayload Base64URL エンコード済み JSON（署名なし）
   * @param secure Secure 属性（prod=true/local=false）
   * @param maxAgeSeconds Max-Age（秒）
   */
  public void setUiCookies(
      HttpServletResponse response,
      String base64UrlPayload,
      boolean secure,
      long maxAgeSeconds) {
    ResponseCookie ui =
        ResponseCookie.from("UI", base64UrlPayload)
            .httpOnly(false)
            .secure(secure)
            .sameSite("Lax")
            .path("/")
            .maxAge(maxAgeSeconds)
            .build();
    response.addHeader("Set-Cookie", ui.toString());
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
}

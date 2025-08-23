package com.capgemini.estimate.poc.estimate_api.auth;

import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

/**
 * 認証関連の Cookie（AT/RT）を生成・削除するユーティリティ。
 * <p>
 * - AT: アクセス用の短命 JWT。HttpOnly/SameSite=Lax/Path=/ で配布。
 * - RT: リフレッシュ用の長命 JWT。HttpOnly/SameSite=Strict/Path=/api/auth/refresh で配布。
 * <p>
 * 環境に応じて Secure 属性を付与（prod=true/local=false）。
 */
@Service
public class AuthCookieService {

  /**
   * AT の Set-Cookie をレスポンスに付与する（RT は使用しない）。
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

  /** AT の削除用 Set-Cookie（Max-Age=0）を付与する。 */
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
}


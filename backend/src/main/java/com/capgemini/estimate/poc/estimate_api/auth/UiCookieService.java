package com.capgemini.estimate.poc.estimate_api.auth;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

/**
 * UI 用ヒント情報を Cookie で配布するサービス。
 * <p>
 * - UI: Base64URL でエンコード済みの JSON ペイロード（非 HttpOnly）
 * - 署名は付与しない簡素構成（最低限の動作確認用）
 */
@Service
public class UiCookieService {

  /** UI の Set-Cookie を付与する（署名は付与しない）。 */
  public void setUiCookies(
      HttpServletResponse response,
      String base64UrlPayload,
      boolean secure,
      long maxAgeSeconds) {
    // UI は画面から参照するため HttpOnly=false、改ざん検出は UI_SIG で行う
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

  /** UI の削除用 Set-Cookie を付与する。 */
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

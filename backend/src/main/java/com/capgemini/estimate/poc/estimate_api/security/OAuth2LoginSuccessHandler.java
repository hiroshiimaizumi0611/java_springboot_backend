package com.capgemini.estimate.poc.estimate_api.security;

import com.capgemini.estimate.poc.estimate_api.auth.CookieUtil;
import com.capgemini.estimate.poc.estimate_api.auth.RedisUtil;
import com.capgemini.estimate.poc.estimate_api.auth.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * OAuth2/OIDC ログイン成功時の後処理。
 * <p>
 * - 端末セッション用の sid/ver を生成
 * - 自前 AT/RT を発行して Cookie に設定（RT は Path を /api/auth/refresh に限定）
 * - Redis に端末セッション情報（ver/lastSeen）を登録
 * - UI/UI_SIG を生成して Cookie に設定
 * - 最後に SPA のルートへリダイレクト
 */
@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

  private final JwtUtil jwtUtil;
  private final CookieUtil cookieUtil;
  private final CsrfTokenRepository csrfTokenRepository;
  
  private final RedisUtil redisUtil;
  @Value("${app.jwt.at-ttl-minutes:10}")
  private long atTtlMinutes;

  public OAuth2LoginSuccessHandler(
      JwtUtil jwtUtil,
      CookieUtil cookieUtil,
      RedisUtil redisUtil,
      CsrfTokenRepository csrfTokenRepository) {
    this.jwtUtil = jwtUtil;
    this.cookieUtil = cookieUtil;
    this.redisUtil = redisUtil;
    this.csrfTokenRepository = csrfTokenRepository;
  }

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws IOException, ServletException {
    // IdP のユーザー情報からユーザー識別子と表示名を決定（シンプルな優先順）
    String username = extractUsername(authentication);

    // 端末セッション識別子とセッションバージョン
    String sid = UUID.randomUUID().toString();
    long ver = 1L;

    // 自前 AT を発行（TTL は設定値を使用）
    long ttlSeconds = atTtlMinutes * 60;
    String at = jwtUtil.createAccessToken(username, sid, ver, ttlSeconds);
    // 端末セッション情報を作成
    redisUtil.upsertOnLogin(username, sid, ver);

    // HttpSession に sid/ver/uid を保存（AT 再発行や UI クッキー生成に参照）
    var httpSession = request.getSession(true);
    httpSession.setAttribute("sid", sid);
    httpSession.setAttribute("ver", ver);
    httpSession.setAttribute("uid", username);
    httpSession.setAttribute("principalName", authentication.getName());

    // Cookie 配布（prod は Secure=true, local は false）
    boolean secure = cookieUtil.isSecureCookie();
    cookieUtil.setAuthCookies(response, at, Duration.ofMinutes(atTtlMinutes), secure);
    cookieUtil.setUiCookie(response, username, secure, Duration.ofMinutes(atTtlMinutes));

    // CSRF トークンをログイン成功レスポンスで発行（Cookie に保存）
    CsrfToken token = csrfTokenRepository.generateToken(request);
    csrfTokenRepository.saveToken(token, request, response);

    // SPA のルートへ返す
    response.sendRedirect("/");
  }

  /** OIDC/OAuth2User から username を抽出（preferred_username 前提: なければ getName）。 */
  private String extractUsername(Authentication authentication) {
    if (authentication.getPrincipal() instanceof OAuth2User user) {
      Object value = user.getAttributes().get("preferred_username");
      if (value != null) {
        String text = String.valueOf(value).trim();
        if (!text.isEmpty()) {
          return text;
        }
      }
    }
    return authentication.getName();
  }

}

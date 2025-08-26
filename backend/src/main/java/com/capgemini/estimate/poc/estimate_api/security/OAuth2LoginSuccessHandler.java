package com.capgemini.estimate.poc.estimate_api.security;

import com.capgemini.estimate.poc.estimate_api.auth.CookieUtil;
import com.capgemini.estimate.poc.estimate_api.auth.RedisUtil;
import com.capgemini.estimate.poc.estimate_api.auth.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
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
  
  private final RedisUtil redisUtil;
  private final Environment environment;
  private final ObjectMapper objectMapper = new ObjectMapper();
  @Value("${app.jwt.at-ttl-minutes:10}")
  private long atTtlMinutes;

  public OAuth2LoginSuccessHandler(
      JwtUtil jwtUtil,
      CookieUtil cookieUtil,
      RedisUtil redisUtil,
      Environment environment) {
    this.jwtUtil = jwtUtil;
    this.cookieUtil = cookieUtil;
    this.redisUtil = redisUtil;
    this.environment = environment;
  }

  /** local 以外のプロファイルでは Secure Cookie を有効にする。 */
  private boolean isSecureCookies() {
    return !environment.acceptsProfiles(Profiles.of("local"));
  }

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws IOException, ServletException {
    // IdP のユーザー情報からユーザー識別子と表示名を決定
    String username = extractUsername(authentication);
    String displayName = extractDisplayName(authentication, username);

    // 端末セッション識別子とセッションバージョン（初期値=1）
    String sid = UUID.randomUUID().toString();
    long ver = 1L;

    // 自前 AT を発行（TTL は設定値を使用）
    long ttlSeconds = atTtlMinutes * 60;
    String at = jwtUtil.createAccessToken(username, sid, ver, ttlSeconds);
    // 端末セッション情報を作成（RT は用いないため jti は保存しない）
    redisUtil.upsertOnLogin(username, sid, ver);

    // HttpSession に sid/ver/uid/displayName を保存（AT 再発行や UI クッキー生成に参照）
    var httpSession = request.getSession(true);
    httpSession.setAttribute("sid", sid);
    httpSession.setAttribute("ver", ver);
    httpSession.setAttribute("uid", username);
    httpSession.setAttribute("displayName", displayName);
    // AuthorizedClientRepository のキーとなる principal.name も保存
    httpSession.setAttribute("principalName", authentication.getName());

    // Cookie 配布（prod は Secure=true, local は false）
    boolean secure = isSecureCookies();
    cookieUtil.setAuthCookies(response, at, Duration.ofMinutes(atTtlMinutes), secure);

    // UI 表示用ヒント（Base64URL JSON）と署名
    Map<String, Object> ui = new HashMap<>();
    ui.put("uid", username);
    ui.put("name", displayName);
    ui.put("exp", Instant.now().plusSeconds(ttlSeconds).getEpochSecond());
    String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
        objectMapper.writeValueAsString(ui).getBytes());
    cookieUtil.setUiCookies(response, payload, secure, Duration.ofMinutes(atTtlMinutes).toSeconds());

    // SPA のルートへ返す
    response.sendRedirect("/");
  }

  /** OIDC/OAuth2User から username を抽出（email 優先）。 */
  private String extractUsername(Authentication authentication) {
    if (authentication.getPrincipal() instanceof OidcUser oidc) {
      String email = oidc.getEmail();
      if (email != null) return email;
      return oidc.getSubject();
    }
    if (authentication.getPrincipal() instanceof OAuth2User user) {
      Object email = user.getAttributes().get("email");
      if (email != null) return String.valueOf(email);
      return user.getName();
    }
    return authentication.getName();
  }

  /** OIDC/OAuth2User から displayName を抽出（name → email → username の順でフォールバック）。 */
  private String extractDisplayName(Authentication authentication, String usernameFallback) {
    if (authentication.getPrincipal() instanceof OidcUser oidc) {
      String name = oidc.getFullName();
      if (name == null) name = oidc.getGivenName();
      if (name == null) name = oidc.getFamilyName();
      if (name == null) name = oidc.getEmail();
      return name != null ? name : usernameFallback;
    }
    if (authentication.getPrincipal() instanceof OAuth2User user) {
      Object name = user.getAttributes().get("name");
      if (name == null) name = user.getAttributes().get("email");
      if (name != null) return String.valueOf(name);
    }
    return usernameFallback;
  }
}

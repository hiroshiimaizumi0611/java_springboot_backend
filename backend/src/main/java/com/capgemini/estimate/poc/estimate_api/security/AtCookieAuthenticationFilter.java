package com.capgemini.estimate.poc.estimate_api.security;

import com.capgemini.estimate.poc.estimate_api.auth.AuthCookieService;
import com.capgemini.estimate.poc.estimate_api.auth.SessionService;
import com.capgemini.estimate.poc.estimate_api.auth.TokenService;
import com.capgemini.estimate.poc.estimate_api.auth.UiCookieService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * AT（Cookie）での認証を担当するフィルタ。
 * <p>
 * - Cookie から AT を取得し、JWT を検証
 * - Redis のセッションメタ（sid/ver/lastSeen）と照合し、無操作タイムアウトも判定
 * - 正常なら Spring Security に認証をセット
 * - ver 不一致/タイムアウト時は（refresh を除き）Cookie を削除し ver++
 * - 署名/exp 不正時も（refresh を除き）Cookie を削除
 */
@Component
public class AtCookieAuthenticationFilter extends OncePerRequestFilter {

  private final TokenService tokenService;
  private final SessionService sessionService;
  private final AuthCookieService authCookieService;
  private final UiCookieService uiCookieService;
  private final Environment environment;

  @Value("${app.session.idle-timeout-minutes:120}")
  private long idleTimeoutMinutes;

  public AtCookieAuthenticationFilter(
      TokenService tokenService,
      SessionService sessionService,
      AuthCookieService authCookieService,
      UiCookieService uiCookieService,
      Environment environment) {
    this.tokenService = tokenService;
    this.sessionService = sessionService;
    this.authCookieService = authCookieService;
    this.uiCookieService = uiCookieService;
    this.environment = environment;
  }

  /** local 以外のプロファイルでは Secure Cookie を有効にする。 */
  private boolean isSecureCookies() {
    return !environment.acceptsProfiles(Profiles.of("local"));
  }

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
      throws ServletException, IOException {
    // コンテキストパス配下でも正しく判定できるようにする
    String uri = request.getRequestURI();
    String ctx = request.getContextPath();
    String path = (ctx != null && !ctx.isEmpty() && uri.startsWith(ctx)) ? uri.substring(ctx.length()) : uri;
    boolean isRefreshPath = "/api/auth/refresh".equals(path) || path.endsWith("/api/auth/refresh");
    String jwt = extractAtFromCookie(request);
    if (jwt != null && SecurityContextHolder.getContext().getAuthentication() == null) {
      try {
        // JWT を検証し、必要なクレーム（sub/sid/ver）を取り出す
        Map<String, Object> claims = tokenService.parseClaims(jwt);
        String subject = (String) claims.get("sub");
        String sid = (String) claims.get("sid");
        long ver = ((Number) claims.getOrDefault("ver", 1)).longValue();

        // Redis メタと照合し、無操作タイムアウト未超過なら lastSeen を更新
        boolean ok = sessionService.validateAccessAndTouch(sid, ver, idleTimeoutMinutes);
        if (ok) {
          UsernamePasswordAuthenticationToken auth =
              new UsernamePasswordAuthenticationToken(subject, null, List.of());
          auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
          SecurityContextHolder.getContext().setAuthentication(auth);
        } else {
          // ver 不一致 or アイドルタイムアウト
          if (!isRefreshPath) {
            if (sid != null) {
              // セッションを失効させるため ver++
              sessionService.incrementVer(sid);
            }
            boolean secure = isSecureCookies();
            // ブラウザから AT/RT を削除
            authCookieService.clearAuthCookies(response, secure);
            uiCookieService.clearUiCookies(response, secure);
          }
        }
      } catch (Exception e) {
        // 署名不正や exp 失効など JWT 自体が無効
        if (!isRefreshPath) {
          boolean secure = isSecureCookies();
          authCookieService.clearAuthCookies(response, secure);
          uiCookieService.clearUiCookies(response, secure);
        }
      }
    }
    filterChain.doFilter(request, response);
  }

  /** Cookie から AT を取り出す（存在しない場合は null）。 */
  private String extractAtFromCookie(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) return null;
    for (Cookie c : cookies) {
      if ("AT".equals(c.getName())) {
        return c.getValue();
      }
    }
    return null;
  }
}

package com.capgemini.estimate.poc.estimate_api.security;

import com.capgemini.estimate.poc.estimate_api.auth.CookieUtil;
import com.capgemini.estimate.poc.estimate_api.auth.RedisUtil;
import com.capgemini.estimate.poc.estimate_api.auth.JwtUtil;
 
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * AT（Cookie）での認証を担当するフィルタ。
 * <p>
 * - Cookie から AT を取得し、JWT を検証
 * - Redis の端末セッション情報（sid/ver/lastSeen）と照合し、無操作タイムアウトも判定
 * - 正常なら Spring Security に認証をセット
 * - ver 不一致/タイムアウト時は（refresh を除き）Cookie を削除し ver++
 * - 署名/exp 不正時も（refresh を除き）Cookie を削除
 */
/**
 * Cookie の AT（自前 JWT）を検証し、認証済みコンテキストを構築するフィルタ。
 * <p>
 * - JWT の署名/exp を検証後、Redis 上の端末セッション（sid/ver/lastSeen）と照合する。
 * - 無操作タイムアウトや ver 不一致を検知した場合は、refresh パス以外で Cookie を削除して失効させる。
 */
@Component
public class AtCookieAuthenticationFilter extends OncePerRequestFilter {

  private final JwtUtil jwtUtil;
  private final RedisUtil redisUtil;
  private final CookieUtil cookieUtil;

  @Value("${app.session.idle-timeout-minutes:120}")
  private long idleTimeoutMinutes;

  /**
   * コンストラクタ。
   *
   * @param jwtUtil JWT の生成/検証ユーティリティ
   * @param redisUtil 端末セッション情報の照合/更新ユーティリティ
   * @param cookieUtil Cookie の配布/削除ユーティリティ
   */
  public AtCookieAuthenticationFilter(
      JwtUtil jwtUtil,
      RedisUtil redisUtil,
      CookieUtil cookieUtil) {
    this.jwtUtil = jwtUtil;
    this.redisUtil = redisUtil;
    this.cookieUtil = cookieUtil;
  }

  /**
   * リクエスト毎に AT を検証し、認証済みコンテキストを設定する。
   *
   * <p>refresh エンドポイント以外で失効を検知した場合は、端末セッション ver を進め、
   * ブラウザ側の認証系 Cookie を削除する。
   */
  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
      throws ServletException, IOException {
    boolean isRefreshPath = new AntPathRequestMatcher("/api/auth/refresh").matches(request);
    String jwt = extractAtFromCookie(request);
    if (jwt != null && SecurityContextHolder.getContext().getAuthentication() == null) {
      try {
        // JWT を検証し、必要なクレーム（sub/sid/ver）を取り出す
        Map<String, Object> claims = jwtUtil.parseClaims(jwt);
        String subject = (String) claims.get("sub");
        String sid = (String) claims.get("sid");
        long ver = ((Number) claims.getOrDefault("ver", 1)).longValue();

        // 端末セッション情報と照合し、無操作タイムアウト未超過なら lastSeen を更新
        boolean ok = redisUtil.validateAccessAndTouch(sid, ver, idleTimeoutMinutes);
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
              redisUtil.incrementVer(sid);
            }
            boolean secure = cookieUtil.isSecureCookie();
            // ブラウザから AT/RT を削除
            cookieUtil.clearAuthCookies(response, secure);
            cookieUtil.clearUiCookies(response, secure);
          }
        }
      } catch (Exception e) {
        // 署名不正や exp 失効など JWT 自体が無効
        if (!isRefreshPath) {
          boolean secure = cookieUtil.isSecureCookie();
          cookieUtil.clearAuthCookies(response, secure);
          cookieUtil.clearUiCookies(response, secure);
        }
      }
    }
    filterChain.doFilter(request, response);
  }

  /** Cookie から access_token を取り出す（存在しない場合は null）。 */
  private String extractAtFromCookie(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) return null;
    for (Cookie c : cookies) {
      if ("access_token".equals(c.getName())) {
        return c.getValue();
      }
    }
    return null;
  }
}

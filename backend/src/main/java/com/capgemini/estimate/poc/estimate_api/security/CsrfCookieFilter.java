package com.capgemini.estimate.poc.estimate_api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 遅延生成される CsrfToken を確実に実体化し、CookieCsrfTokenRepository に保存させるためのフィルタ。
 * <p>
 * Spring Security 6 では、アプリコードが CsrfToken にアクセスしない場合にトークンが保存されず、
 * 既存の XSRF-TOKEN Cookie が削除（Max-Age=0）されることがある。これを防ぐ目的で、
 * 毎リクエストで CsrfToken#getToken() を呼び出して遅延トークンを確定させる。
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(CsrfCookieFilter.class);

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
      throws ServletException, IOException {
    CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
    String incomingToken = request.getHeader("X-XSRF-TOKEN");
    String outgoingToken = (csrfToken != null) ? csrfToken.getToken() : null;

    if (csrfToken != null) {
        log.debug("{} = Incoming: {}, Outgoing: {}", csrfToken.getHeaderName(), incomingToken, outgoingToken);
        // Expose the token back to the client if needed
        response.setHeader("X-XSRF-TOKEN", csrfToken.getToken());
    } else {
        log.warn("CSRF Token is null or not present in the request.");
    }
    filterChain.doFilter(request, response);
  }
}

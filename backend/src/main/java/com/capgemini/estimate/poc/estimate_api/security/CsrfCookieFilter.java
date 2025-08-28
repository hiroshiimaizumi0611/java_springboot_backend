package com.capgemini.estimate.poc.estimate_api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

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

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
      throws ServletException, IOException {
    CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
    if (token != null) {
      // 遅延トークンを実体化（この呼び出しで Cookie への保存が行われる）
      token.getToken();
    }
    filterChain.doFilter(request, response);
  }
}


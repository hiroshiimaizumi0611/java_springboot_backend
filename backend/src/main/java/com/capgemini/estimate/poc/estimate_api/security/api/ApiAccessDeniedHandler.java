package com.capgemini.estimate.poc.estimate_api.security.api;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.InvalidCsrfTokenException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;

/**
 * /api/** 用の AccessDeniedHandler。403（主にCSRF）時の状況をログに出す。
 */
public class ApiAccessDeniedHandler implements AccessDeniedHandler {
  private static final Logger log = LoggerFactory.getLogger(ApiAccessDeniedHandler.class);

  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
      throws IOException, ServletException {
    String uri = request.getRequestURI();
    String method = request.getMethod();

    if (accessDeniedException instanceof MissingCsrfTokenException) {
      log.warn("403 CSRF missing: method={}, uri={}", method, uri);
    } else if (accessDeniedException instanceof InvalidCsrfTokenException invalid) {
      // 実トークンは出さず、発生箇所のみ簡潔に残す
      log.warn("403 CSRF invalid: method={}, uri={} (mismatch)", method, uri);
    } else if (accessDeniedException instanceof CsrfException) {
      log.warn("403 CSRF: method={}, uri={}, type={}", method, uri, accessDeniedException.getClass().getSimpleName());
    } else {
      log.warn("403 AccessDenied: method={}, uri={}, ex={}", method, uri, accessDeniedException.toString());
    }

    response.sendError(HttpServletResponse.SC_FORBIDDEN);
  }
}


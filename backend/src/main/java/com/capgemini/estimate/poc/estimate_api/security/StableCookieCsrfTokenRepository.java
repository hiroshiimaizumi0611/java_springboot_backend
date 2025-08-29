package com.capgemini.estimate.poc.estimate_api.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/**
 * CookieCsrfTokenRepository をラップし、saveToken(null, ...) 時にクッキー削除(Max-Age=0)を行わない安定化リポジトリ。
 * トークンが null の場合は、既存クッキーの値を保持（必要に応じて再保存）し、不要な削除→再発行の揺れを防ぐ。
 */
public class StableCookieCsrfTokenRepository implements CsrfTokenRepository {

  private final CookieCsrfTokenRepository delegate;

  public StableCookieCsrfTokenRepository(CookieCsrfTokenRepository delegate) {
    this.delegate = delegate;
  }

  @Override
  public CsrfToken generateToken(HttpServletRequest request) {
    return delegate.generateToken(request);
  }

  @Override
  public void saveToken(CsrfToken token, HttpServletRequest request, HttpServletResponse response) {
    if (token == null) {
      // 既存のトークンがある場合は維持（削除は行わない）
      CsrfToken existing = delegate.loadToken(request);
      if (existing != null) {
        delegate.saveToken(existing, request, response);
      }
      // 何もない場合は no-op（クッキー未発行のまま）
      return;
    }
    delegate.saveToken(token, request, response);
  }

  @Override
  public CsrfToken loadToken(HttpServletRequest request) {
    return delegate.loadToken(request);
  }
}

package com.capgemini.estimate.poc.estimate_api.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.http.HttpStatus;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Autowired AtCookieAuthenticationFilter atCookieAuthenticationFilter;
  @Autowired OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
  @Autowired Environment environment;

  /**
   * API 用のセキュリティチェーン（/api/**）。
   * <p>
   * 特徴:
   * - 対象: `/api/**` のみ（その他は {@link #webFilterChain(HttpSecurity)} が担当）
   * - セッション: stateless（サーバ側セキュリティコンテキストは保存しない）
   * - 認証: {@link AtCookieAuthenticationFilter} が `access_token` Cookie を検証して認証をセット
   * - CSRF: {@link org.springframework.security.web.csrf.CookieCsrfTokenRepository#withHttpOnlyFalse()} を使用（SPA が JS で `XSRF-TOKEN` を読み取り、XHR にヘッダ付与）
   *   - CSRF 補助: {@link CsrfCookieFilter} を {@link org.springframework.security.web.csrf.CsrfFilter} の後段に追加し、
   *     遅延トークンを毎リクエストで実体化して Cookie への保存を確実化（XSRF-TOKEN が消える事象を防止）
   * - 例外: 未認証は 401 を返却（ブラウザリダイレクトはしない）
   * - 許可: `/api/csrf`, `/api/auth/refresh`, `/api/auth/logout` は常に許可
   *   - refresh はフィルタで Cookie をクリアしない特例（リフレッシュ判定に委ねる）
   * - 備考: stateless のため {@link org.springframework.security.web.context.NullSecurityContextRepository} を使用し、毎リクエストで検証する
   */
  @Bean
  @Order(1)
  SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {

    CsrfTokenRequestAttributeHandler  csrfTokenRequestAttributeHandler  =  new CsrfTokenRequestAttributeHandler (); 
    csrfTokenRequestAttributeHandler.setCsrfRequestAttributeName( "_csrf" ); 

    return http
        .securityMatcher(new AntPathRequestMatcher("/api/**"))
        .csrf(csrf -> csrf
            .csrfTokenRepository(csrfTokenRepository())
            .csrfTokenRequestHandler(csrfTokenRequestAttributeHandler))
        .formLogin(formLogin -> formLogin.disable())
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .securityContext(sc -> sc.securityContextRepository(new NullSecurityContextRepository()))
        .exceptionHandling(ex -> ex
            .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/csrf", "/api/auth/refresh", "/api/auth/logout").permitAll()
            .anyRequest().authenticated())
        .addFilterAfter(new CsrfCookieFilter(csrfTokenRepository()), CsrfFilter.class)
        .addFilterBefore(atCookieAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        .build();
  }

  /**
   * Web/OAuth2 用のセキュリティチェーン（/api/** 以外）。
   * <p>
   * 特徴:
   * - 対象: `/api/**` 以外（SPA ルート遷移や OAuth2 ログインフロー）
   * - セッション: stateful（Spring Security が HttpSession を利用）
   * - ログイン: formLogin は無効化し、OAuth2/OIDC ログインを使用
   * - 成功後: {@link OAuth2LoginSuccessHandler} で端末セッション登録・Cookie 配布・リダイレクト
   * - CSRF: API 同様、{@link org.springframework.security.web.csrf.CookieCsrfTokenRepository#withHttpOnlyFalse()} を使用
   *   - CSRF 補助: {@link CsrfCookieFilter} を {@link org.springframework.security.web.csrf.CsrfFilter} の後段に追加し、
   *     遅延トークンの実体化と Cookie 保存を安定化
   * - 許可: `/oauth2/authorization/**`, `/login/oauth2/code/**` は匿名許可
   * - 備考: セッションフィクセーション対策で `sessionFixation().newSession()` を有効化
   */
  @Bean
  @Order(2)
  SecurityFilterChain webFilterChain(HttpSecurity http) throws Exception {

    CsrfTokenRequestAttributeHandler  csrfTokenRequestAttributeHandler  =  new CsrfTokenRequestAttributeHandler (); 
    csrfTokenRequestAttributeHandler.setCsrfRequestAttributeName( "_csrf" ); 

    return http
        .csrf(csrf -> csrf
            .csrfTokenRepository(csrfTokenRepository())
            .csrfTokenRequestHandler(csrfTokenRequestAttributeHandler))
        .formLogin(formLogin -> formLogin.disable())
        .sessionManagement(session -> session.sessionFixation().newSession())
        .oauth2Login(oauth -> oauth.successHandler(oAuth2LoginSuccessHandler))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(
                "/oauth2/authorization/**",
                "/login/oauth2/code/**").permitAll()
            .anyRequest().authenticated())
        .addFilterAfter(new CsrfCookieFilter(csrfTokenRepository()), CsrfFilter.class)
        .build();
  }

  @Bean
  public AuthenticationManager authenticationManager(
      AuthenticationConfiguration authenticationConfiguration) throws Exception {
    return authenticationConfiguration.getAuthenticationManager();
  }

  @Bean
    public CsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository base = CookieCsrfTokenRepository.withHttpOnlyFalse();
        base.setCookiePath("/");
        base.setCookieName("XSRF-TOKEN");
        base.setHeaderName("X-XSRF-TOKEN");
        boolean secure = !environment.acceptsProfiles(Profiles.of("local"));
        base.setSecure(secure);
        base.setCookieMaxAge(3600); // 1 hour

        StableCookieCsrfTokenRepository stable = new StableCookieCsrfTokenRepository(base);
        // もし将来プロパティを上書きしたい場合は stable の setter でも設定可能
        return stable;
    }
}

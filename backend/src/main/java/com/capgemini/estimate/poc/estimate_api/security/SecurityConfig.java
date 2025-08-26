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
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Autowired AtCookieAuthenticationFilter atCookieAuthenticationFilter;
  @Autowired OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

  /** API 用の stateless セキュリティチェーン（/api/**） */
  @Bean
  @Order(1)
  SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
    return http
        .securityMatcher(new AntPathRequestMatcher("/api/**"))
        .csrf(csrf -> csrf
            .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
            .csrfTokenRequestHandler(new org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler()))
        .formLogin(formLogin -> formLogin.disable())
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .securityContext(sc -> sc.securityContextRepository(new NullSecurityContextRepository()))
        .exceptionHandling(ex -> ex
            .authenticationEntryPoint(new org.springframework.security.web.authentication.HttpStatusEntryPoint(
                org.springframework.http.HttpStatus.UNAUTHORIZED)))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/csrf", "/api/auth/refresh", "/api/auth/logout").permitAll()
            .anyRequest().authenticated())
        .addFilterBefore(atCookieAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        .build();
  }

  /** Web/OAuth2 用の stateful セキュリティチェーン（/api/** 以外） */
  @Bean
  @Order(2)
  SecurityFilterChain webFilterChain(HttpSecurity http) throws Exception {
    return http
        .csrf(csrf -> csrf
            .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
            .csrfTokenRequestHandler(new org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler()))
        .formLogin(formLogin -> formLogin.disable())
        .sessionManagement(session -> session.sessionFixation().newSession())
        .oauth2Login(oauth -> oauth.successHandler(oAuth2LoginSuccessHandler))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(
                "/oauth2/authorization/**",
                "/login/oauth2/code/**").permitAll()
            .anyRequest().authenticated())
        .build();
  }

  @Bean
  public AuthenticationManager authenticationManager(
      AuthenticationConfiguration authenticationConfiguration) throws Exception {
    return authenticationConfiguration.getAuthenticationManager();
  }
}

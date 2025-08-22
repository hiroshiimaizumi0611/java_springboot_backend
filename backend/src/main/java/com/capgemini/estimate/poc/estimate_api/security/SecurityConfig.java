package com.capgemini.estimate.poc.estimate_api.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Autowired AtCookieAuthenticationFilter atCookieAuthenticationFilter;
  @Autowired OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

  @Bean
  SecurityFilterChain filterchain(HttpSecurity http) throws Exception {
    return http
        .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
        .formLogin(formLogin -> formLogin.disable())
        .oauth2Login(oauth -> oauth.successHandler(oAuth2LoginSuccessHandler))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/api/csrf",
                        "/api/auth/refresh",
                        "/api/auth/logout",
                        "/oauth2/authorization/**",
                        "/login/oauth2/code/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(atCookieAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        .build();
  }

  @Bean
  public AuthenticationManager authenticationManager(
      AuthenticationConfiguration authenticationConfiguration) throws Exception {
    return authenticationConfiguration.getAuthenticationManager();
  }
}

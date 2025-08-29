package com.capgemini.estimate.poc.estimate_api.security;

import com.capgemini.estimate.poc.estimate_api.exception.UserNotFoundException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

/**
 * 開発/検証用の簡易 {@link UserDetailsService} 設定。
 * <p>
 * 固定ユーザー {@code testuser}/{@code password} を返却し、その他は {@code UserNotFoundException} を投げる。
 */
@Configuration
public class UserConfig {
  /**
   * 開発用の固定ユーザーを返す {@link UserDetailsService} を提供する。
   */
  @Bean
  public UserDetailsService userDetailsService() {
    return username -> {
      if (!"testuser".equals(username)) {
        throw new UserNotFoundException(username);
      }
      UserDetails user =
          User.withUsername("testuser").password("{noop}password").roles("USER").build();
      return user;
    };
  }
}

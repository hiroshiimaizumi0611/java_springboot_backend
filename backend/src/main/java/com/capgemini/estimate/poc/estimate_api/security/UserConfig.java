package com.capgemini.estimate.poc.estimate_api.security;

import com.capgemini.estimate.poc.estimate_api.exception.UserNotFoundException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

@Configuration
public class UserConfig {
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

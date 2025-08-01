package com.capgemini.estimate.poc.estimate_api.infrastructure.config;

import org.seasar.doma.jdbc.Config;
import org.seasar.doma.jdbc.criteria.QueryDsl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomaCriteriaConfig {

  @Bean
  QueryDsl queryDsl(Config config) {
    return new QueryDsl(config);
  }
}

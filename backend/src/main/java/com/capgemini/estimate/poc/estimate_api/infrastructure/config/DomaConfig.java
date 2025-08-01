package com.capgemini.estimate.poc.estimate_api.infrastructure.config;

import javax.sql.DataSource;
import org.seasar.doma.jdbc.Config;
import org.seasar.doma.jdbc.Naming;
import org.seasar.doma.jdbc.dialect.Dialect;
import org.seasar.doma.jdbc.dialect.OracleDialect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;

@Configuration
public class DomaConfig implements Config {

  private final DataSource dataSource;
  private final Dialect dialect;

  public DomaConfig(DataSource dataSource) {
    this.dataSource = new TransactionAwareDataSourceProxy(dataSource);
    this.dialect = new OracleDialect();
  }

  @Override
  public DataSource getDataSource() {
    return this.dataSource;
  }

  @Override
  public Dialect getDialect() {
    return this.dialect;
  }

  @Override
  public Naming getNaming() {
    return Naming.SNAKE_UPPER_CASE;
  }

  @Bean
  DataSourceTransactionManager transactionManager(DataSource actualDataSource) {
    return new DataSourceTransactionManager(actualDataSource);
  }
}

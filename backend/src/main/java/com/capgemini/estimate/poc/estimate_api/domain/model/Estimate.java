package com.capgemini.estimate.poc.estimate_api.domain.model;

import org.seasar.doma.Entity;
import org.seasar.doma.Id;
import org.seasar.doma.Metamodel;

@Entity(metamodel = @Metamodel())
public class Estimate {
  @Id public String id;
  public String title;
  public String customerName;
  public Integer totalAmount;
}

package com.capgemini.estimate.poc.estimate_api.infrastructure.repository;

import com.capgemini.estimate.poc.estimate_api.domain.model.Estimate;
import com.capgemini.estimate.poc.estimate_api.domain.model.Estimate_;
import com.capgemini.estimate.poc.estimate_api.domain.repository.EstimateRepository;
import java.util.List;
import java.util.UUID;
import org.seasar.doma.jdbc.criteria.QueryDsl;
import org.springframework.stereotype.Repository;

@Repository
public class EstimateRepositoryImpl implements EstimateRepository {
  private final QueryDsl queryDsl;

  public EstimateRepositoryImpl(QueryDsl queryDsl) {
    this.queryDsl = queryDsl;
  }

  @Override
  public List<Estimate> selectAll() {
    var e = new Estimate_();

    return queryDsl.from(e).fetch();
  }

  @Override
  public void insert(Estimate estimate) {
    estimate.id = UUID.randomUUID().toString();
    var e = new Estimate_();

    queryDsl.insert(e).single(estimate).execute();
  }

  @Override
  public void delete(String id) {
    var e = new Estimate_();

    var estimate = queryDsl.from(e).where(c -> c.eq(e.id, id)).fetchOne();
    queryDsl.delete(e).single(estimate).execute();
  }

  @Override
  public void update(Estimate estimate) {
    var e = new Estimate_();

    var _estimate = queryDsl.from(e).where(c -> c.eq(e.id, estimate.id)).fetchOne();
    _estimate.customerName = estimate.customerName;
    _estimate.title = estimate.title;
    _estimate.totalAmount = estimate.totalAmount;
    queryDsl.update(e).single(estimate).execute();
  }

  @Override
  public Estimate selectById(String id) {
    var e = new Estimate_();

    return queryDsl.from(e).where(c -> c.eq(e.id, id)).fetchOne();
  }
}

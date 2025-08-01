package com.capgemini.estimate.poc.estimate_api.domain.repository;

import com.capgemini.estimate.poc.estimate_api.domain.model.Estimate;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public interface EstimateRepository {
  List<Estimate> selectAll();

  Estimate selectById(String id);

  void insert(Estimate estimate);

  void delete(String id);

  void update(Estimate estimate);
}

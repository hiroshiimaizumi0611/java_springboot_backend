package com.capgemini.estimate.poc.estimate_api.usecase;

import com.capgemini.estimate.poc.estimate_api.domain.model.Estimate;
import com.capgemini.estimate.poc.estimate_api.domain.repository.EstimateRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class EstimateUseCase {

  @Autowired private final EstimateRepository repository;

  public EstimateUseCase(EstimateRepository repository) {
    this.repository = repository;
  }

  public List<Estimate> getAllEstimates() {
    return repository.selectAll();
  }

  public Estimate getEstimate(String id) {
    return repository.selectById(id);
  }

  public void insertEstimate(Estimate estimate) {
    repository.insert(estimate);
  }

  public void deleteEstimate(String id) {
    repository.delete(id);
  }

  public void updateEstimate(Estimate estimate) {
    repository.update(estimate);
  }
}

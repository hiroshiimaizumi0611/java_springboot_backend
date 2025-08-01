package com.capgemini.estimate.poc.estimate_api.domain.model;

public class RefreshRequest {
  private String refreshToken;

  public RefreshRequest(String refreshToken) {
    this.refreshToken = refreshToken;
  }

  public String getRefreshToken() {
    return refreshToken;
  }
}

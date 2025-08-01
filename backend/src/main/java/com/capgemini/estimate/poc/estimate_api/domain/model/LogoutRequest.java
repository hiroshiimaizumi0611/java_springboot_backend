package com.capgemini.estimate.poc.estimate_api.domain.model;

public class LogoutRequest {
  private String refreshToken;

  public LogoutRequest(String refreshToken) {
    this.refreshToken = refreshToken;
  }

  public String getRefreshToken() {
    return refreshToken;
  }
}

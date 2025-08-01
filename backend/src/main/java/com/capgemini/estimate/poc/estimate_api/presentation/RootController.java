package com.capgemini.estimate.poc.estimate_api.presentation;

import org.springframework.web.bind.annotation.*;

@RestController
public class RootController {

  @GetMapping("/")
  public String health() {
    return "Estimate API is running.";
  }
}

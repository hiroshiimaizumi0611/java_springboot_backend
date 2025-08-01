package com.capgemini.estimate.poc.estimate_api.exception;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(Exception.class)
  public ProblemDetail handleAllExceptions(Exception ex) {
    System.out.println("---- handleAllExceptions called ----");
    ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
    pd.setTitle("サーバエラー");
    pd.setDetail(ex.getMessage());
    pd.setType(URI.create("about:blank"));
    return pd;
  }

  @ExceptionHandler(UserNotFoundException.class)
  public ProblemDetail handleUserNotFound(UserNotFoundException ex) {
    System.out.println("---- handleUserNotFound called ----");
    ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
    pd.setTitle("ユーザーが見つかりません");
    pd.setDetail(ex.getMessage());
    pd.setType(URI.create("/problem/user-not-found"));
    return pd;
  }
}

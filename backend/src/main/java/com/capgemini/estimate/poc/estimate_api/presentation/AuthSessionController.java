package com.capgemini.estimate.poc.estimate_api.presentation;

import com.capgemini.estimate.poc.estimate_api.auth.CookieUtil;
import com.capgemini.estimate.poc.estimate_api.auth.RedisUtil;
import com.capgemini.estimate.poc.estimate_api.auth.JwtUtil;
import com.capgemini.estimate.poc.estimate_api.auth.TokenRefreshValidator;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AuthSessionController {

  private final JwtUtil jwtUtil;
  private final CookieUtil cookieUtil;
  private final RedisUtil redisUtil;
  private final TokenRefreshValidator tokenRefreshValidator;
  @Value("${app.jwt.at-ttl-minutes:10}")
  private long atTtlMinutes;

  public AuthSessionController(
      JwtUtil jwtUtil,
      CookieUtil cookieUtil,
      RedisUtil redisUtil,
      TokenRefreshValidator tokenRefreshValidator) {
    this.jwtUtil = jwtUtil;
    this.cookieUtil = cookieUtil;
    this.redisUtil = redisUtil;
    this.tokenRefreshValidator = tokenRefreshValidator;
  }

  @PostMapping("/auth/logout")
  public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
    boolean secure = cookieUtil.isSecureCookie();
    String at = getCookie(request, "access_token");
    String sid = null;

    try {
      if (at != null) {
        Map<String, Object> c = jwtUtil.parseClaims(at);
        sid = (String) c.get("sid");
      }
    } catch (Exception ignored) {}
    if (sid != null) {
      redisUtil.incrementVer(sid);
    }

    cookieUtil.clearAuthCookies(response, secure);
    cookieUtil.clearUiCookies(response, secure);
    
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/auth/refresh")
  public ResponseEntity<Void> refresh(HttpServletRequest request, HttpServletResponse response)
      throws Exception {
    boolean ok = tokenRefreshValidator.canRefresh(request, response);
    boolean secure = cookieUtil.isSecureCookie();

    if (!ok) {
      cookieUtil.clearAuthCookies(response, secure);
      cookieUtil.clearUiCookies(response, secure);
      return ResponseEntity.status(401).build();
    }

    // 新しい access_token/user_info を発行
    var httpSession = request.getSession(false);

    if (httpSession == null) {
      cookieUtil.clearAuthCookies(response, secure);
      cookieUtil.clearUiCookies(response, secure);
      return ResponseEntity.status(401).build();
    }

    String sid = (String) httpSession.getAttribute("sid");
    Long ver = (Long) httpSession.getAttribute("ver");
    String uid = (String) httpSession.getAttribute("uid");
    String principalName = (String) httpSession.getAttribute("principalName");

    if (sid == null || ver == null) {
      cookieUtil.clearAuthCookies(response, secure);
      cookieUtil.clearUiCookies(response, secure);
      return ResponseEntity.status(401).build();
    }

    String subject = (uid != null) ? uid : (principalName != null ? principalName : sid);
    long ttlSeconds = atTtlMinutes * 60L;
    String newAt = jwtUtil.createAccessToken(subject, sid, ver, ttlSeconds);

    cookieUtil.setAuthCookies(response, newAt, Duration.ofSeconds(ttlSeconds), secure);
    cookieUtil.setUiCookie(response, subject, secure, Duration.ofSeconds(ttlSeconds));
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/me")
  public Map<String, Object> me(Authentication authentication) {
    Map<String, Object> body = new HashMap<>();

    body.put("name", authentication != null ? authentication.getName() : null);
    body.put("roles", new String[] {});

    return body;
  }

  @GetMapping("/csrf")
  public Map<String, String> csrf(CsrfToken token) {
    Map<String, String> body = new HashMap<>();

    body.put("headerName", token.getHeaderName());
    body.put("parameterName", token.getParameterName());
    body.put("token", token.getToken());

    return body;
  }

  private String getCookie(HttpServletRequest request, String name) {
    Cookie[] cookies = request.getCookies();

    if (cookies == null) {return null;}

    for (Cookie c : cookies) {
      if (name.equals(c.getName())) return c.getValue();
    }
    return null;
  }
}

package com.capgemini.estimate.poc.estimate_api.presentation;

import com.capgemini.estimate.poc.estimate_api.auth.CookieUtil;
import com.capgemini.estimate.poc.estimate_api.auth.RedisUtil;
import com.capgemini.estimate.poc.estimate_api.auth.JwtUtil;
import com.capgemini.estimate.poc.estimate_api.auth.AuthRefreshService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api")
public class AuthSessionController {

  private final JwtUtil jwtUtil;
  private final CookieUtil cookieUtil;
  private final RedisUtil redisUtil;
  private final Environment environment;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final AuthRefreshService authRefreshService;

  public AuthSessionController(
      JwtUtil jwtUtil,
      CookieUtil cookieUtil,
      RedisUtil redisUtil,
      Environment environment,
      AuthRefreshService authRefreshService) {
    this.jwtUtil = jwtUtil;
    this.cookieUtil = cookieUtil;
    this.redisUtil = redisUtil;
    this.environment = environment;
    this.authRefreshService = authRefreshService;
  }

  private boolean isSecureCookies() {
    return !environment.acceptsProfiles(Profiles.of("local"));
  }

  @PostMapping("/auth/logout")
  public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
    boolean secure = isSecureCookies();
    String at = getCookie(request, "AT");
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
    // FAT になりがちなリフレッシュ処理はサービス層へ委譲
    return authRefreshService.refresh(request, response);
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
    if (cookies == null) return null;
    for (Cookie c : cookies) {
      if (name.equals(c.getName())) return c.getValue();
    }
    return null;
  }
}

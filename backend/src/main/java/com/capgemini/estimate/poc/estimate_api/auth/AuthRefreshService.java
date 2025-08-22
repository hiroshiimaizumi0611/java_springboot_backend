package com.capgemini.estimate.poc.estimate_api.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.stereotype.Service;

@Service
public class AuthRefreshService {

  private final TokenService tokenService;
  private final AuthCookieService authCookieService;
  private final UiCookieService uiCookieService;
  private final Environment environment;
  private final OAuth2AuthorizedClientManager authorizedClientManager;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Value("${app.idp.registration-id:cognito}")
  private String idpRegistrationId;

  public AuthRefreshService(
      TokenService tokenService,
      AuthCookieService authCookieService,
      UiCookieService uiCookieService,
      SessionService sessionService,
      Environment environment,
      OAuth2AuthorizedClientManager authorizedClientManager) {
    this.tokenService = tokenService;
    this.authCookieService = authCookieService;
    this.uiCookieService = uiCookieService;
    this.environment = environment;
    this.authorizedClientManager = authorizedClientManager;
  }

  private boolean isSecureCookies() {
    return !environment.acceptsProfiles(Profiles.of("local"));
  }

  public ResponseEntity<Void> refresh(HttpServletRequest request, HttpServletResponse response)
      throws Exception {
    var httpSession = request.getSession(false);
    if (httpSession == null) {
      return ResponseEntity.status(401).build();
    }
    String sid = (String) httpSession.getAttribute("sid");
    Long ver = (Long) httpSession.getAttribute("ver");
    if (sid == null || ver == null) {
      return ResponseEntity.status(401).build();
    }

    Authentication principal =
        (SecurityContextHolder.getContext().getAuthentication() != null)
            ? SecurityContextHolder.getContext().getAuthentication()
            : new UsernamePasswordAuthenticationToken(sid, null, List.of());

    OAuth2AuthorizeRequest authRequest =
        OAuth2AuthorizeRequest.withClientRegistrationId(idpRegistrationId)
            .principal(principal)
            .attribute(HttpServletRequest.class.getName(), request)
            .attribute(HttpServletResponse.class.getName(), response)
            .build();

    var authorizedClient = authorizedClientManager.authorize(authRequest);
    if (authorizedClient == null || authorizedClient.getAccessToken() == null) {
      boolean secure = isSecureCookies();
      authCookieService.clearAuthCookies(response, secure);
      uiCookieService.clearUiCookies(response, secure);
      return ResponseEntity.status(401).build();
    }

    String uid = (String) httpSession.getAttribute("uid");
    String displayName = (String) httpSession.getAttribute("displayName");
    String subject =
        uid != null ? uid : ((principal != null && principal.getName() != null) ? principal.getName() : sid);
    String newAt = tokenService.createAccessToken(subject, sid, ver, 10 * 60);
    boolean secure = isSecureCookies();
    authCookieService.setAuthCookies(response, newAt, Duration.ofMinutes(10), secure);

    Map<String, Object> ui = new HashMap<>();
    ui.put("uid", subject);
    ui.put("name", displayName != null ? displayName : subject);
    ui.put("exp", Instant.now().plusSeconds(10 * 60).getEpochSecond());
    String payload =
        Base64.getUrlEncoder().withoutPadding().encodeToString(objectMapper.writeValueAsString(ui).getBytes());
    uiCookieService.setUiCookies(response, payload, secure, Duration.ofMinutes(10).toSeconds());

    return ResponseEntity.noContent().build();
  }
}

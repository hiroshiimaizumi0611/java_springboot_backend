package com.capgemini.estimate.poc.estimate_api.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.stereotype.Service;

@Service
public class AuthRefreshService {

  private final JwtUtil jwtUtil;
  private final CookieUtil cookieUtil;
  
  private final RedisUtil redisUtil;
  private final Environment environment;
  private final OAuth2AuthorizedClientManager authorizedClientManager;

  @Value("${app.idp.registration-id:cognito}")
  private String idpRegistrationId;
  @Value("${app.jwt.at-ttl-minutes:10}")
  private long atTtlMinutes;

  public AuthRefreshService(
      JwtUtil jwtUtil,
      CookieUtil cookieUtil,
      RedisUtil redisUtil,
      Environment environment,
      OAuth2AuthorizedClientManager authorizedClientManager) {
    this.jwtUtil = jwtUtil;
    this.cookieUtil = cookieUtil;
    this.redisUtil = redisUtil;
    this.environment = environment;
    this.authorizedClientManager = authorizedClientManager;
  }

  private boolean isSecureCookies() {
    return !environment.acceptsProfiles(Profiles.of("local"));
  }

  /**
   * AT を再発行し、UI Cookie を更新する。
   *
   * @param request HttpServletRequest
   * @param response HttpServletResponse
   * @return 204: 再発行成功、401: 再ログインが必要
   * @throws Exception 変換エラーなど
   */
  public ResponseEntity<Void> refresh(HttpServletRequest request, HttpServletResponse response)
      throws Exception {
    HttpSession httpSession = request.getSession(false);
    if (httpSession == null) {
      return unauthorizedAndClearCookies(response);
    }
    String sid = (String) httpSession.getAttribute("sid");
    Long ver = (Long) httpSession.getAttribute("ver");
    if (sid == null || ver == null) {
      return unauthorizedAndClearCookies(response);
    }

    // Redis 側の ver と HttpSession 側の ver が一致しない場合は、アイドル超過等で失効済みと判断して 401 を返す
    Long redisVer = redisUtil.getVer(sid);
    if (redisVer == null || redisVer.longValue() != ver.longValue()) {
      return unauthorizedAndClearCookies(response);
    }

    // AuthorizedClientRepository は principal.name をキーに HttpSession から取得するため、
    // ログイン時に保存した principalName を最優先、次に uid を使用する
    String principalName = resolvePrincipalName(httpSession);
    Authentication principal = new UsernamePasswordAuthenticationToken(principalName, null, List.of());

    OAuth2AuthorizeRequest authRequest =
        OAuth2AuthorizeRequest.withClientRegistrationId(idpRegistrationId)
            .principal(principal)
            .attribute(HttpServletRequest.class.getName(), request)
            .attribute(HttpServletResponse.class.getName(), response)
            .build();

    OAuth2AuthorizedClient authorizedClient = null;
    try {
      authorizedClient = authorizedClientManager.authorize(authRequest);
    } catch (OAuth2AuthorizationException ex) {
      // fallthrough to 401
    }
    if (authorizedClient == null || authorizedClient.getAccessToken() == null) {
      return unauthorizedAndClearCookies(response);
    }

    String uid = (String) httpSession.getAttribute("uid");
    String displayName = (String) httpSession.getAttribute("displayName");
    String subject =
        uid != null ? uid : ((principal != null && principal.getName() != null) ? principal.getName() : sid);
    long ttlSeconds = atTtlMinutes * 60;
    String newAt = jwtUtil.createAccessToken(subject, sid, ver, ttlSeconds);
    boolean secure = isSecureCookies();
    cookieUtil.setAuthCookies(response, newAt, Duration.ofMinutes(atTtlMinutes), secure);
    cookieUtil.setUiCookie(response, subject, displayName, secure, Duration.ofMinutes(atTtlMinutes));

    return ResponseEntity.noContent().build();
  }


  private String resolvePrincipalName(HttpSession httpSession) {
    String principalName = (String) httpSession.getAttribute("principalName");
    if (principalName == null) { principalName = (String) httpSession.getAttribute("uid"); }
    if (principalName == null && SecurityContextHolder.getContext().getAuthentication() != null) {
      principalName = SecurityContextHolder.getContext().getAuthentication().getName();
    }
    if (principalName == null) { principalName = (String) httpSession.getAttribute("sid"); }
    return principalName;
  }

  private ResponseEntity<Void> unauthorizedAndClearCookies(HttpServletResponse response) {
    boolean secure = isSecureCookies();
    cookieUtil.clearAuthCookies(response, secure);
    cookieUtil.clearUiCookies(response, secure);
    return ResponseEntity.status(401).build();
  }

}

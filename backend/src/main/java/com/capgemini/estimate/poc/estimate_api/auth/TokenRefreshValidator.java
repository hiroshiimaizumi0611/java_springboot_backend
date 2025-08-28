package com.capgemini.estimate.poc.estimate_api.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.stereotype.Service;

@Service
public class TokenRefreshValidator {

  private final RedisUtil redisUtil;
  private final OAuth2AuthorizedClientManager authorizedClientManager;

  @Value("${app.idp.registration-id:cognito}")
  private String idpRegistrationId;
  @Value("${app.jwt.at-ttl-minutes:10}")
  private long atTtlMinutes;

  public TokenRefreshValidator(
      RedisUtil redisUtil,
      OAuth2AuthorizedClientManager authorizedClientManager) {
    this.redisUtil = redisUtil;
    this.authorizedClientManager = authorizedClientManager;
  }

  /**
   * リフレッシュの前提条件（HttpSession, Redis ver, IdP AuthorizedClient）を検証する。
   * Cookie や HTTP ステータスの制御は行わない。
   *
   * @return 全てOKなら true、NG なら false
   */
  public boolean canRefresh(HttpServletRequest request, HttpServletResponse response) {
    HttpSession httpSession = request.getSession(false);
    if (httpSession == null) {
      return false;
    }

    SessionData session = extractSessionData(httpSession);
    if (session == null) {
      return false;
    }

    // Redis の ver と一致しなければ失効と見なし 401
    Long redisVer = redisUtil.getVer(session.sid());
    if (redisVer == null || redisVer.longValue() != session.ver().longValue()) {
      return false;
    }

    // Authorized Client の生存確認（必要に応じて RT で更新）
    String principalName = session.principalName();
    if (principalName == null || principalName.isBlank()) {
      return false;
    }
    Authentication principal = new UsernamePasswordAuthenticationToken(principalName, null, List.of());
    OAuth2AuthorizedClient authorizedClient = authorizeIdpClient(request, response, principal);
    if (authorizedClient == null || authorizedClient.getAccessToken() == null) {
      return false;
    }

    return true;
  }


  /**
   * HttpSession から認証に必要な情報を抽出。sid/ver 欠如時は null。
   */
  private SessionData extractSessionData(HttpSession session) {
    String sid = (String) session.getAttribute("sid");
    Long ver = (Long) session.getAttribute("ver");
    if (sid == null || ver == null) {
      return null;
    }
    String uid = (String) session.getAttribute("uid");
    String principalName = (String) session.getAttribute("principalName");
    return new SessionData(sid, ver, uid, principalName);
  }

  /**
   * IdP の Authorized Client を取得（必要に応じて RT 更新）。失敗時は null。
   */
  private OAuth2AuthorizedClient authorizeIdpClient(
      HttpServletRequest request,
      HttpServletResponse response,
      Authentication principal) {
    OAuth2AuthorizeRequest authRequest =
        OAuth2AuthorizeRequest.withClientRegistrationId(idpRegistrationId)
            .principal(principal)
            .attribute(HttpServletRequest.class.getName(), request)
            .attribute(HttpServletResponse.class.getName(), response)
            .build();
    try {
      return authorizedClientManager.authorize(authRequest);
    } catch (OAuth2AuthorizationException ex) {
      return null;
    }
  }

  private static record SessionData(
      String sid, Long ver, String uid, String principalName) {}
}

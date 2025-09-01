package com.capgemini.estimate.poc.estimate_api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.test.util.ReflectionTestUtils;

/** {@code TokenRefreshValidator} の単体テスト。 */
@ExtendWith(MockitoExtension.class)
class TokenRefreshValidatorTest {

  @Mock private RedisUtil redisUtil;
  @Mock private OAuth2AuthorizedClientManager authorizedClientManager;
  @InjectMocks private TokenRefreshValidator validator;

  @BeforeEach
  void init() {
    // プロパティ注入（デフォルト値: cognito）
    ReflectionTestUtils.setField(validator, "idpRegistrationId", "cognito");
  }

  /**
   * HttpSession が存在しない場合は前提条件を満たさないため false。
   */
  @Test
  void canRefresh_noHttpSession_returnsFalse() {
    HttpServletRequest req = mock(HttpServletRequest.class);
    HttpServletResponse res = mock(HttpServletResponse.class);
    when(req.getSession(false)).thenReturn(null);

    assertThat(validator.canRefresh(req, res)).isFalse();
  }

  /**
   * sid または ver が HttpSession に無い場合は false。
   */
  @Test
  void canRefresh_missingSidOrVer_returnsFalse() {
    HttpServletRequest req = mock(HttpServletRequest.class);
    HttpServletResponse res = mock(HttpServletResponse.class);
    HttpSession session = mock(HttpSession.class);
    when(req.getSession(false)).thenReturn(session);
    // sid が無いケース（ver はあっても良い）
    when(session.getAttribute("sid")).thenReturn(null);
    when(session.getAttribute("ver")).thenReturn(1L);

    assertThat(validator.canRefresh(req, res)).isFalse();
  }

  /**
   * Redis 側の ver が null（セッション情報が見つからない）なら false。
   */
  @Test
  void canRefresh_redisVerNull_returnsFalse() {
    HttpServletRequest req = mock(HttpServletRequest.class);
    HttpServletResponse res = mock(HttpServletResponse.class);
    HttpSession session = mock(HttpSession.class);
    when(req.getSession(false)).thenReturn(session);
    when(session.getAttribute("sid")).thenReturn("sid");
    when(session.getAttribute("ver")).thenReturn(1L);

    when(redisUtil.getVer("sid")).thenReturn(null);

    assertThat(validator.canRefresh(req, res)).isFalse();
  }

  /**
   * Redis の ver と HttpSession の ver が不一致なら false。
   */
  @Test
  void canRefresh_redisVerMismatch_returnsFalse() {
    HttpServletRequest req = mock(HttpServletRequest.class);
    HttpServletResponse res = mock(HttpServletResponse.class);
    HttpSession session = mock(HttpSession.class);
    when(req.getSession(false)).thenReturn(session);
    when(session.getAttribute("sid")).thenReturn("sid");
    when(session.getAttribute("ver")).thenReturn(1L);

    when(redisUtil.getVer("sid")).thenReturn(2L);

    assertThat(validator.canRefresh(req, res)).isFalse();
  }

  /**
   * principalName が空の場合は false。
   */
  @Test
  void canRefresh_principalNameBlank_returnsFalse() {
    HttpServletRequest req = mock(HttpServletRequest.class);
    HttpServletResponse res = mock(HttpServletResponse.class);
    HttpSession session = mock(HttpSession.class);
    when(req.getSession(false)).thenReturn(session);
    when(session.getAttribute("sid")).thenReturn("sid");
    when(session.getAttribute("ver")).thenReturn(1L);
    when(redisUtil.getVer("sid")).thenReturn(1L);
    // extractSessionData は uid と principalName の両方を参照するため uid もスタブ
    when(session.getAttribute("uid")).thenReturn("uid");
    when(session.getAttribute("principalName")).thenReturn("");

    assertThat(validator.canRefresh(req, res)).isFalse();
  }

  /**
   * IdP の authorize が null を返す（取得失敗）場合は false。
   */
  @Test
  void canRefresh_authorizeReturnsNull_returnsFalse() {
    HttpServletRequest req = mock(HttpServletRequest.class);
    HttpServletResponse res = mock(HttpServletResponse.class);
    HttpSession session = mock(HttpSession.class);
    when(req.getSession(false)).thenReturn(session);
    when(session.getAttribute("sid")).thenReturn("sid");
    when(session.getAttribute("ver")).thenReturn(1L);
    when(redisUtil.getVer("sid")).thenReturn(1L);
    when(session.getAttribute("uid")).thenReturn("uid");
    when(session.getAttribute("principalName")).thenReturn("user@example");

    when(authorizedClientManager.authorize(any())).thenReturn(null);

    assertThat(validator.canRefresh(req, res)).isFalse();
  }

  /**
   * AuthorizedClient は返るが AccessToken が null の場合は false。
   */
  @Test
  void canRefresh_authorizeReturnsClientWithoutAccessToken_returnsFalse() {
    HttpServletRequest req = mock(HttpServletRequest.class);
    HttpServletResponse res = mock(HttpServletResponse.class);
    HttpSession session = mock(HttpSession.class);
    when(req.getSession(false)).thenReturn(session);
    when(session.getAttribute("sid")).thenReturn("sid");
    when(session.getAttribute("ver")).thenReturn(1L);
    when(redisUtil.getVer("sid")).thenReturn(1L);
    when(session.getAttribute("uid")).thenReturn("uid");
    when(session.getAttribute("principalName")).thenReturn("user@example");

    OAuth2AuthorizedClient client = mock(OAuth2AuthorizedClient.class);
    when(client.getAccessToken()).thenReturn(null);
    when(authorizedClientManager.authorize(any())).thenReturn(client);

    assertThat(validator.canRefresh(req, res)).isFalse();
  }

  /**
   * すべての前提が満たされ、AccessToken も取得できれば true。
   */
  @Test
  void canRefresh_allOk_returnsTrue() {
    HttpServletRequest req = mock(HttpServletRequest.class);
    HttpServletResponse res = mock(HttpServletResponse.class);
    HttpSession session = mock(HttpSession.class);
    when(req.getSession(false)).thenReturn(session);
    when(session.getAttribute("sid")).thenReturn("sid");
    when(session.getAttribute("ver")).thenReturn(1L);
    when(redisUtil.getVer("sid")).thenReturn(1L);
    when(session.getAttribute("uid")).thenReturn("uid");
    when(session.getAttribute("principalName")).thenReturn("user@example");

    OAuth2AuthorizedClient client = mock(OAuth2AuthorizedClient.class);
    OAuth2AccessToken token = mock(OAuth2AccessToken.class);
    when(client.getAccessToken()).thenReturn(token);
    when(authorizedClientManager.authorize(any())).thenReturn(client);

    assertThat(validator.canRefresh(req, res)).isTrue();
  }
}

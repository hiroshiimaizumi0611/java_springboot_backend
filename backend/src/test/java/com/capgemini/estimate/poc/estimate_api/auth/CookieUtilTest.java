package com.capgemini.estimate.poc.estimate_api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletResponse;

/** {@code CookieUtil} の単体テスト。 */
class CookieUtilTest {

  /** local プロファイルでは Secure Cookie は無効。 */
  @Test
  void isSecureCookie_localProfile_returnsFalse() {
    MockEnvironment env = new MockEnvironment();
    env.setActiveProfiles("local");

    CookieUtil util = new CookieUtil(env);
    assertThat(util.isSecureCookie()).isFalse();
  }

  /** local 以外のプロファイルでは Secure Cookie が有効。 */
  @Test
  void isSecureCookie_nonLocal_returnsTrue() {
    MockEnvironment env = new MockEnvironment();
    env.setActiveProfiles("prod");

    CookieUtil util = new CookieUtil(env);
    assertThat(util.isSecureCookie()).isTrue();
  }

  /** access_token の Set-Cookie が想定どおり（Secure=true）。 */
  @Test
  void setAuthCookies_secureTrue_addsHttpOnlyLaxPathAndMaxAgeAndSecure() {
    MockHttpServletResponse response = new MockHttpServletResponse();
    Environment env = new MockEnvironment();
    CookieUtil util = new CookieUtil(env);

    util.setAuthCookies(response, "jwt-token", Duration.ofMinutes(10), true);

    List<String> headers = response.getHeaders("Set-Cookie");
    assertThat(headers).hasSize(1);
    String c = headers.get(0);

    assertThat(c).startsWith("access_token=jwt-token");
    assertThat(c).contains("Max-Age=600");
    assertThat(c).contains("Path=/");
    assertThat(c).contains("SameSite=Lax");
    assertThat(c).contains("HttpOnly");
    assertThat(c).contains("Secure");
  }

  /** access_token の Set-Cookie が想定どおり（Secure=false）。 */
  @Test
  void setAuthCookies_secureFalse_omitsSecureFlag() {
    MockHttpServletResponse response = new MockHttpServletResponse();
    Environment env = new MockEnvironment();
    CookieUtil util = new CookieUtil(env);

    util.setAuthCookies(response, "jwt-token", Duration.ofSeconds(30), false);

    List<String> headers = response.getHeaders("Set-Cookie");
    assertThat(headers).hasSize(1);
    String c = headers.get(0);

    assertThat(c).startsWith("access_token=jwt-token");
    assertThat(c).contains("Max-Age=30");
    assertThat(c).contains("Path=/");
    assertThat(c).contains("SameSite=Lax");
    assertThat(c).contains("HttpOnly");
    assertThat(c).doesNotContain("Secure");
  }

  /** access_token 削除（Max-Age=0）。 */
  @Test
  void clearAuthCookies_setsMaxAgeZero() {
    MockHttpServletResponse response = new MockHttpServletResponse();
    CookieUtil util = new CookieUtil(new MockEnvironment());

    util.clearAuthCookies(response, true);

    List<String> headers = response.getHeaders("Set-Cookie");
    assertThat(headers).hasSize(1);
    String c = headers.get(0);

    assertThat(c).startsWith("access_token=");
    assertThat(c).contains("Max-Age=0");
    assertThat(c).contains("Path=/");
    assertThat(c).contains("SameSite=Lax");
    assertThat(c).contains("HttpOnly");
    assertThat(c).contains("Secure");
  }

  /** user_info 削除（Max-Age=0, HttpOnlyなし）。 */
  @Test
  void clearUiCookies_setsMaxAgeZero_andNoHttpOnly() {
    MockHttpServletResponse response = new MockHttpServletResponse();
    CookieUtil util = new CookieUtil(new MockEnvironment());

    util.clearUiCookies(response, false);

    List<String> headers = response.getHeaders("Set-Cookie");
    assertThat(headers).hasSize(1);
    String c = headers.get(0);

    assertThat(c).startsWith("user_info=");
    assertThat(c).contains("Max-Age=0");
    assertThat(c).contains("Path=/");
    assertThat(c).contains("SameSite=Lax");
    assertThat(c).doesNotContain("HttpOnly");
    assertThat(c).doesNotContain("Secure");
  }

  /** user_info の Set-Cookie（属性と値の存在のみ簡潔に確認）。 */
  @Test
  void setUiCookie_setsAttributes_andPayloadExists() {
    MockHttpServletResponse response = new MockHttpServletResponse();
    CookieUtil util = new CookieUtil(new MockEnvironment());

    String uid = "user-123";
    Duration ttl = Duration.ofMinutes(10);

    util.setUiCookie(response, uid, true, ttl);

    List<String> headers = response.getHeaders("Set-Cookie");
    assertThat(headers).hasSize(1);
    String c = headers.get(0);

    // 属性検証（Max-Age は ttl 秒、Lax、Path=/、HttpOnlyなし、Secureあり）
    assertThat(c).startsWith("user_info=");
    assertThat(c).contains("Max-Age=" + ttl.toSeconds());
    assertThat(c).contains("Path=/");
    assertThat(c).contains("SameSite=Lax");
    assertThat(c).doesNotContain("HttpOnly");
    assertThat(c).contains("Secure");

    String value = c.substring("user_info=".length(), c.indexOf(';'));
    assertThat(value).isNotBlank();
  }
}

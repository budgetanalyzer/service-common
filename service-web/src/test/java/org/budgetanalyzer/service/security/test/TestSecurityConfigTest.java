package org.budgetanalyzer.service.security.test;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Unit tests for {@link TestSecurityConfig}.
 *
 * <p>Verifies the mock JWT decoder behaves correctly when custom JWTs are set.
 */
class TestSecurityConfigTest {

  @AfterEach
  void cleanup() {
    TestSecurityConfig.CUSTOM_JWT.remove();
  }

  @Test
  void jwtDecoder_shouldReturnDefaultJwtWhenNoCustomJwtSet() {
    var config = new TestSecurityConfig();
    var decoder = config.jwtDecoder();

    var jwt = decoder.decode("any-token-string");

    assertThat(jwt).isNotNull();
    assertThat(jwt.getSubject()).isEqualTo("test-user");
    assertThat(jwt.getClaimAsString("scope")).isEqualTo("openid profile email");
  }

  @Test
  void jwtDecoder_shouldReturnCustomJwtWhenSet() {
    var customJwt = JwtTestBuilder.user("custom-user").withScopes("admin:all").build();
    TestSecurityConfig.CUSTOM_JWT.set(customJwt);

    var config = new TestSecurityConfig();
    var decoder = config.jwtDecoder();

    var jwt = decoder.decode("any-token-string");

    assertThat(jwt).isNotNull();
    assertThat(jwt.getSubject()).isEqualTo("custom-user");
    assertThat(jwt.getClaimAsString("scope")).isEqualTo("admin:all");
  }

  @Test
  void jwtDecoder_shouldSwitchBetweenCustomAndDefault() {
    var config = new TestSecurityConfig();
    var decoder = config.jwtDecoder();

    // First decode - default JWT
    var defaultJwt = decoder.decode("token");
    assertThat(defaultJwt.getSubject()).isEqualTo("test-user");

    // Set custom JWT
    var customJwt = JwtTestBuilder.user("custom-user").build();
    TestSecurityConfig.CUSTOM_JWT.set(customJwt);

    // Second decode - custom JWT
    var customDecoded = decoder.decode("token");
    assertThat(customDecoded.getSubject()).isEqualTo("custom-user");

    // Remove custom JWT
    TestSecurityConfig.CUSTOM_JWT.remove();

    // Third decode - back to default JWT
    var backToDefault = decoder.decode("token");
    assertThat(backToDefault.getSubject()).isEqualTo("test-user");
  }

  @Test
  void jwtDecoder_shouldBePrimaryBean() throws NoSuchMethodException {
    var method = TestSecurityConfig.class.getMethod("jwtDecoder");
    var primaryAnnotation =
        method.getAnnotation(org.springframework.context.annotation.Primary.class);

    assertThat(primaryAnnotation).isNotNull();
  }

  @Test
  void jwtDecoder_shouldReturnJwtDecoderType() {
    var config = new TestSecurityConfig();
    var decoder = config.jwtDecoder();

    assertThat(decoder).isInstanceOf(JwtDecoder.class);
  }
}

package org.budgetanalyzer.service.security.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link JwtTestBuilder}.
 *
 * <p>Verifies the fluent builder API correctly constructs JWT tokens with custom claims.
 */
class JwtTestBuilderTest {

  @Test
  void defaultJwt_shouldHaveStandardClaims() {
    var jwt = JwtTestBuilder.defaultJwt();

    assertThat(jwt.getSubject()).isEqualTo("test-user");
    assertThat(jwt.getClaimAsString("scope")).isEqualTo("openid profile email");
    assertThat(jwt.getIssuer().toString()).isEqualTo("https://test-issuer.example.com/");
    assertThat(jwt.getAudience()).containsExactly("https://test-api.example.com");
    assertThat(jwt.getIssuedAt()).isNotNull();
    assertThat(jwt.getExpiresAt()).isNotNull();
    assertThat(jwt.getTokenValue()).isEqualTo("test-token");
  }

  @Test
  void user_shouldSetCustomSubject() {
    var jwt = JwtTestBuilder.user("john-doe").build();

    assertThat(jwt.getSubject()).isEqualTo("john-doe");
  }

  @Test
  void admin_shouldSetAdminSubject() {
    var jwt = JwtTestBuilder.admin().build();

    assertThat(jwt.getSubject()).isEqualTo("admin-user");
  }

  @Test
  void withScopes_shouldSetScopeClaimAsSpaceSeparatedString() {
    var jwt =
        JwtTestBuilder.user("test").withScopes("read:data", "write:data", "admin:all").build();

    assertThat(jwt.getClaimAsString("scope")).isEqualTo("read:data write:data admin:all");
  }

  @Test
  void withIssuer_shouldSetCustomIssuer() {
    var jwt = JwtTestBuilder.user("test").withIssuer("https://custom-issuer.com/").build();

    assertThat(jwt.getIssuer().toString()).isEqualTo("https://custom-issuer.com/");
  }

  @Test
  void withAudience_shouldSetCustomAudience() {
    var jwt = JwtTestBuilder.user("test").withAudience("https://custom-api.com").build();

    assertThat(jwt.getAudience()).containsExactly("https://custom-api.com");
  }

  @Test
  void withIssuedAt_shouldSetCustomIssuedAt() {
    var issuedAt = Instant.parse("2024-01-01T00:00:00Z");
    var jwt = JwtTestBuilder.user("test").withIssuedAt(issuedAt).build();

    assertThat(jwt.getIssuedAt()).isEqualTo(issuedAt);
  }

  @Test
  void withExpiresAt_shouldSetCustomExpiresAt() {
    var issuedAt = Instant.parse("2024-01-01T00:00:00Z");
    var expiresAt = Instant.parse("2024-12-31T23:59:59Z");
    var jwt = JwtTestBuilder.user("test").withIssuedAt(issuedAt).withExpiresAt(expiresAt).build();

    assertThat(jwt.getExpiresAt()).isEqualTo(expiresAt);
  }

  @Test
  void withClaim_shouldAddCustomClaim() {
    var jwt =
        JwtTestBuilder.user("test")
            .withClaim("organization_id", "org-123")
            .withClaim("email", "test@example.com")
            .build();

    assertThat(jwt.getClaimAsString("organization_id")).isEqualTo("org-123");
    assertThat(jwt.getClaimAsString("email")).isEqualTo("test@example.com");
  }

  @Test
  void build_shouldSupportMethodChaining() {
    var jwt =
        JwtTestBuilder.user("john-doe")
            .withScopes("read:data", "write:data")
            .withIssuer("https://my-issuer.com/")
            .withAudience("https://my-api.com")
            .withClaim("email", "john@example.com")
            .build();

    assertThat(jwt.getSubject()).isEqualTo("john-doe");
    assertThat(jwt.getClaimAsString("scope")).isEqualTo("read:data write:data");
    assertThat(jwt.getIssuer().toString()).isEqualTo("https://my-issuer.com/");
    assertThat(jwt.getAudience()).containsExactly("https://my-api.com");
    assertThat(jwt.getClaimAsString("email")).isEqualTo("john@example.com");
  }

  @Test
  void build_shouldIncludeStandardHeaders() {
    var jwt = JwtTestBuilder.defaultJwt();

    assertThat(jwt.getHeaders()).containsEntry("alg", "RS256");
    assertThat(jwt.getHeaders()).containsEntry("typ", "JWT");
  }
}

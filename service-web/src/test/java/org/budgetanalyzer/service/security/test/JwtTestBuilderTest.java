package org.budgetanalyzer.service.security.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

/**
 * Unit tests for {@link JwtTestBuilder}.
 *
 * <p>Verifies the fluent builder API correctly constructs JWT tokens with custom claims.
 */
class JwtTestBuilderTest {

  @Test
  void defaultJwt_shouldHaveStandardClaims() {
    var jwt = JwtTestBuilder.defaultJwt();

    assertThat(jwt.getSubject()).isEqualTo("usr_test123");
    assertThat(jwt.getClaimAsString("iss")).isEqualTo("session-gateway");
    assertThat(jwt.getClaimAsString("idp_sub")).isEqualTo("idp|test-user-idp-sub");
    assertThat(jwt.<List<String>>getClaim("roles")).containsExactly("USER");
    assertThat(jwt.<List<String>>getClaim("permissions"))
        .containsExactly("transactions:read", "accounts:read", "budgets:read");
    assertThat(jwt.getIssuedAt()).isNotNull();
    assertThat(jwt.getExpiresAt()).isNotNull();
    assertThat(jwt.getTokenValue()).isEqualTo("test-token");
    // Gateway JWTs do not include scope or audience by default
    assertThat(jwt.getClaims()).doesNotContainKey("scope");
    assertThat(jwt.getAudience()).isNullOrEmpty();
  }

  @Test
  void user_shouldSetCustomSubject() {
    var jwt = JwtTestBuilder.user("usr_john123").build();

    assertThat(jwt.getSubject()).isEqualTo("usr_john123");
  }

  @Test
  void admin_shouldSetAdminSubjectAndFullPermissions() {
    var jwt = JwtTestBuilder.admin().build();

    assertThat(jwt.getSubject()).isEqualTo("usr_admin456");
    assertThat(jwt.<List<String>>getClaim("roles")).containsExactly("ADMIN");
    assertThat(jwt.<List<String>>getClaim("permissions"))
        .containsExactlyInAnyOrder(
            "transactions:read",
            "transactions:write",
            "transactions:delete",
            "accounts:read",
            "accounts:write",
            "accounts:delete",
            "budgets:read",
            "budgets:write",
            "budgets:delete",
            "users:read",
            "users:write",
            "users:delete",
            "roles:read",
            "roles:write",
            "audit:read",
            "currencies:read",
            "currencies:write",
            "statementformats:read",
            "statementformats:write",
            "statementformats:delete");
  }

  @Test
  void withRoles_shouldSetRolesClaimAsList() {
    var jwt = JwtTestBuilder.user("usr_test123").withRoles("ADMIN", "USER").build();

    assertThat(jwt.<List<String>>getClaim("roles")).containsExactly("ADMIN", "USER");
  }

  @Test
  void withPermissions_shouldSetPermissionsClaimAsList() {
    var jwt =
        JwtTestBuilder.user("usr_test123")
            .withPermissions("transactions:read", "transactions:write", "accounts:read")
            .build();

    assertThat(jwt.<List<String>>getClaim("permissions"))
        .containsExactly("transactions:read", "transactions:write", "accounts:read");
  }

  @Test
  void withIdpSub_shouldSetIdpSubClaim() {
    var jwt = JwtTestBuilder.user("usr_test123").withIdpSub("idp|custom-idp-sub").build();

    assertThat(jwt.getClaimAsString("idp_sub")).isEqualTo("idp|custom-idp-sub");
  }

  @Test
  void withScopes_shouldSetScopeClaimWhenExplicitlySet() {
    var jwt =
        JwtTestBuilder.user("usr_test123")
            .withScopes("read:data", "write:data", "admin:all")
            .build();

    assertThat(jwt.getClaimAsString("scope")).isEqualTo("read:data write:data admin:all");
  }

  @Test
  void withAudience_shouldSetAudienceClaimWhenExplicitlySet() {
    var jwt = JwtTestBuilder.user("usr_test123").withAudience("https://custom-api.com").build();

    assertThat(jwt.getAudience()).containsExactly("https://custom-api.com");
  }

  @Test
  void withIssuer_shouldSetCustomIssuer() {
    var jwt = JwtTestBuilder.user("usr_test123").withIssuer("custom-gateway").build();

    assertThat(jwt.getClaimAsString("iss")).isEqualTo("custom-gateway");
  }

  @Test
  void withIssuedAt_shouldSetCustomIssuedAt() {
    var issuedAt = Instant.parse("2024-01-01T00:00:00Z");
    var jwt = JwtTestBuilder.user("usr_test123").withIssuedAt(issuedAt).build();

    assertThat(jwt.getIssuedAt()).isEqualTo(issuedAt);
  }

  @Test
  void withExpiresAt_shouldSetCustomExpiresAt() {
    var issuedAt = Instant.parse("2024-01-01T00:00:00Z");
    var expiresAt = Instant.parse("2024-12-31T23:59:59Z");
    var jwt =
        JwtTestBuilder.user("usr_test123").withIssuedAt(issuedAt).withExpiresAt(expiresAt).build();

    assertThat(jwt.getExpiresAt()).isEqualTo(expiresAt);
  }

  @Test
  void withClaim_shouldAddCustomClaim() {
    var jwt =
        JwtTestBuilder.user("usr_test123")
            .withClaim("organization_id", "org-123")
            .withClaim("email", "test@example.com")
            .build();

    assertThat(jwt.getClaimAsString("organization_id")).isEqualTo("org-123");
    assertThat(jwt.getClaimAsString("email")).isEqualTo("test@example.com");
  }

  @Test
  void build_shouldSupportMethodChaining() {
    var jwt =
        JwtTestBuilder.user("usr_john123")
            .withRoles("ADMIN", "USER")
            .withPermissions("transactions:read", "transactions:write")
            .withIdpSub("idp|john-idp-sub")
            .withIssuer("custom-gateway")
            .withClaim("email", "john@example.com")
            .build();

    assertThat(jwt.getSubject()).isEqualTo("usr_john123");
    assertThat(jwt.<List<String>>getClaim("roles")).containsExactly("ADMIN", "USER");
    assertThat(jwt.<List<String>>getClaim("permissions"))
        .containsExactly("transactions:read", "transactions:write");
    assertThat(jwt.getClaimAsString("idp_sub")).isEqualTo("idp|john-idp-sub");
    assertThat(jwt.getClaimAsString("iss")).isEqualTo("custom-gateway");
    assertThat(jwt.getClaimAsString("email")).isEqualTo("john@example.com");
  }

  @Test
  void build_shouldIncludeStandardHeaders() {
    var jwt = JwtTestBuilder.defaultJwt();

    assertThat(jwt.getHeaders()).containsEntry("alg", "RS256");
    assertThat(jwt.getHeaders()).containsEntry("typ", "JWT");
  }

  @Test
  void extractAuthorities_shouldExtractPermissionsAsDirectAuthorities() {
    var jwt =
        JwtTestBuilder.user("usr_test123")
            .withPermissions("transactions:read", "accounts:write")
            .withRoles()
            .build();

    var authorities = JwtTestBuilder.extractAuthorities(jwt);

    assertThat(authorities)
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("transactions:read", "accounts:write");
  }

  @Test
  void extractAuthorities_shouldExtractRolesWithRolePrefix() {
    var jwt =
        JwtTestBuilder.user("usr_test123").withPermissions().withRoles("ADMIN", "USER").build();

    var authorities = JwtTestBuilder.extractAuthorities(jwt);

    assertThat(authorities)
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("ROLE_ADMIN", "ROLE_USER");
  }

  @Test
  void extractAuthorities_shouldCombinePermissionsAndRoles() {
    var jwt =
        JwtTestBuilder.user("usr_test123")
            .withPermissions("transactions:read", "accounts:write")
            .withRoles("USER")
            .build();

    var authorities = JwtTestBuilder.extractAuthorities(jwt);

    assertThat(authorities)
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("transactions:read", "accounts:write", "ROLE_USER");
  }

  @Test
  void extractAuthorities_shouldReturnEmptyWhenNoClaimsPresent() {
    var jwt = JwtTestBuilder.user("usr_test123").withPermissions().withRoles().build();

    var authorities = JwtTestBuilder.extractAuthorities(jwt);

    assertThat(authorities).isEmpty();
  }

  @Test
  void extractAuthorities_shouldMatchDefaultJwtExpectedAuthorities() {
    var jwt = JwtTestBuilder.defaultJwt();

    var authorities = JwtTestBuilder.extractAuthorities(jwt);

    assertThat(authorities)
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("transactions:read", "accounts:read", "budgets:read", "ROLE_USER");
  }

  @Test
  void extractAuthorities_shouldMatchAdminJwtExpectedAuthorities() {
    var jwt = JwtTestBuilder.admin().build();

    var authorities = JwtTestBuilder.extractAuthorities(jwt);

    assertThat(authorities).hasSize(21);
    assertThat(authorities)
        .extracting(GrantedAuthority::getAuthority)
        .contains(
            "transactions:read",
            "transactions:write",
            "transactions:delete",
            "accounts:read",
            "accounts:write",
            "accounts:delete",
            "budgets:read",
            "budgets:write",
            "budgets:delete",
            "users:read",
            "users:write",
            "users:delete",
            "roles:read",
            "roles:write",
            "audit:read",
            "currencies:read",
            "currencies:write",
            "statementformats:read",
            "statementformats:write",
            "statementformats:delete",
            "ROLE_ADMIN");
  }
}

package org.budgetanalyzer.service.security.test;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.GrantedAuthority;

/**
 * Unit tests for {@link ClaimsHeaderTestBuilder}.
 *
 * <p>Verifies the fluent builder API correctly constructs claims headers for tests.
 */
class ClaimsHeaderTestBuilderTest {

  @Test
  void defaultUser_shouldHaveCorrectHeaders() {
    var headers = ClaimsHeaderTestBuilder.defaultUser().buildHeaders();

    assertThat(headers).containsEntry("X-User-Id", "usr_test123");
    assertThat(headers)
        .containsEntry("X-Permissions", "transactions:read,accounts:read,budgets:read");
    assertThat(headers).containsEntry("X-Roles", "USER");
  }

  @Test
  void user_shouldSetCustomUserId() {
    var headers = ClaimsHeaderTestBuilder.user("usr_custom789").buildHeaders();

    assertThat(headers).containsEntry("X-User-Id", "usr_custom789");
  }

  @Test
  void admin_shouldSetAdminUserAndFullPermissions() {
    var headers = ClaimsHeaderTestBuilder.admin().buildHeaders();

    assertThat(headers).containsEntry("X-User-Id", "usr_admin456");
    assertThat(headers).containsEntry("X-Roles", "ADMIN");

    var permissions = headers.get("X-Permissions");
    assertThat(permissions).contains("transactions:read");
    assertThat(permissions).contains("transactions:write");
    assertThat(permissions).contains("transactions:delete");
    assertThat(permissions).contains("accounts:read");
    assertThat(permissions).contains("accounts:write");
    assertThat(permissions).contains("accounts:delete");
    assertThat(permissions).contains("budgets:read");
    assertThat(permissions).contains("budgets:write");
    assertThat(permissions).contains("budgets:delete");
    assertThat(permissions).contains("users:read");
    assertThat(permissions).contains("users:write");
    assertThat(permissions).contains("users:delete");
    assertThat(permissions).contains("roles:read");
    assertThat(permissions).contains("roles:write");
    assertThat(permissions).contains("audit:read");
    assertThat(permissions).contains("currencies:read");
    assertThat(permissions).contains("currencies:write");
    assertThat(permissions).contains("statementformats:read");
    assertThat(permissions).contains("statementformats:write");
    assertThat(permissions).contains("statementformats:delete");
  }

  @Test
  void withRoles_shouldSetCorrectHeader() {
    var headers =
        ClaimsHeaderTestBuilder.user("usr_test123").withRoles("ADMIN", "USER").buildHeaders();

    assertThat(headers).containsEntry("X-Roles", "ADMIN,USER");
  }

  @Test
  void withPermissions_shouldSetCorrectHeader() {
    var headers =
        ClaimsHeaderTestBuilder.user("usr_test123")
            .withPermissions("transactions:read", "transactions:write")
            .buildHeaders();

    assertThat(headers).containsEntry("X-Permissions", "transactions:read,transactions:write");
  }

  @Test
  void withEmptyRoles_shouldOmitRolesHeader() {
    var headers = ClaimsHeaderTestBuilder.user("usr_test123").withRoles().buildHeaders();

    assertThat(headers).doesNotContainKey("X-Roles");
  }

  @Test
  void withEmptyPermissions_shouldOmitPermissionsHeader() {
    var headers = ClaimsHeaderTestBuilder.user("usr_test123").withPermissions().buildHeaders();

    assertThat(headers).doesNotContainKey("X-Permissions");
  }

  @Test
  void buildHeaders_shouldReturnCorrectMap() {
    var headers =
        ClaimsHeaderTestBuilder.user("usr_abc123")
            .withPermissions("accounts:read")
            .withRoles("USER")
            .buildHeaders();

    assertThat(headers).hasSize(3);
    assertThat(headers).containsEntry("X-User-Id", "usr_abc123");
    assertThat(headers).containsEntry("X-Permissions", "accounts:read");
    assertThat(headers).containsEntry("X-Roles", "USER");
  }

  @Test
  void postProcessRequest_shouldAddHeadersToRequest() {
    var builder =
        ClaimsHeaderTestBuilder.user("usr_abc123")
            .withPermissions("transactions:read", "accounts:read")
            .withRoles("USER");

    var request = new MockHttpServletRequest();
    builder.postProcessRequest(request);

    assertThat(request.getHeader("X-User-Id")).isEqualTo("usr_abc123");
    assertThat(request.getHeader("X-Permissions")).isEqualTo("transactions:read,accounts:read");
    assertThat(request.getHeader("X-Roles")).isEqualTo("USER");
  }

  @Test
  void authoritiesFor_shouldCombinePermissionsAndRoles() {
    var authorities =
        ClaimsHeaderTestBuilder.authoritiesFor("transactions:read,accounts:read", "ADMIN");

    assertThat(authorities)
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("transactions:read", "accounts:read", "ROLE_ADMIN");
  }

  @Test
  void authoritiesFor_shouldHandleNullPermissions() {
    var authorities = ClaimsHeaderTestBuilder.authoritiesFor(null, "USER");

    assertThat(authorities).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_USER");
  }

  @Test
  void authoritiesFor_shouldHandleNullRoles() {
    var authorities = ClaimsHeaderTestBuilder.authoritiesFor("transactions:read", null);

    assertThat(authorities)
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("transactions:read");
  }

  @Test
  void authoritiesFor_shouldHandleBothNull() {
    var authorities = ClaimsHeaderTestBuilder.authoritiesFor(null, null);

    assertThat(authorities).isEmpty();
  }

  @Test
  void authoritiesFor_shouldTrimWhitespace() {
    var authorities =
        ClaimsHeaderTestBuilder.authoritiesFor(
            " transactions:read , accounts:read ", " ADMIN , USER ");

    assertThat(authorities)
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("transactions:read", "accounts:read", "ROLE_ADMIN", "ROLE_USER");
  }
}

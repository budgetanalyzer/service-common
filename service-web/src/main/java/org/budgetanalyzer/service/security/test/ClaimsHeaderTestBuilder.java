package org.budgetanalyzer.service.security.test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import org.budgetanalyzer.service.security.ClaimsHeaderAuthenticationFilter;

/**
 * Fluent builder for creating test claims headers matching the Envoy ext_authz header shape.
 *
 * <p><b>WARNING: FOR TESTING ONLY</b> - This builder creates mock claims headers for integration
 * tests. Do NOT use in production code.
 *
 * <p>Implements {@link RequestPostProcessor} for seamless MockMvc integration. Also provides a
 * {@link #buildHeaders()} method for unit tests needing raw header maps.
 *
 * <p><b>Usage with MockMvc:</b>
 *
 * <pre>{@code
 * // Default test user with basic permissions
 * mockMvc.perform(get("/api/resource")
 *     .with(ClaimsHeaderTestBuilder.defaultUser()));
 *
 * // Custom user with specific permissions
 * mockMvc.perform(get("/api/resource")
 *     .with(ClaimsHeaderTestBuilder.user("usr_john123")
 *         .withPermissions("transactions:read", "transactions:write")));
 *
 * // Admin user with all permissions
 * mockMvc.perform(get("/api/resource")
 *     .with(ClaimsHeaderTestBuilder.admin()));
 * }</pre>
 *
 * <p><b>Usage for unit tests (header map):</b>
 *
 * <pre>{@code
 * Map<String, String> headers = ClaimsHeaderTestBuilder.user("usr_test123")
 *     .withPermissions("transactions:read")
 *     .buildHeaders();
 * }</pre>
 *
 * <p><b>Thread Safety:</b> This builder is NOT thread-safe. Create a new instance per test.
 *
 * @see TestClaimsSecurityConfig
 * @see ClaimsHeaderAuthenticationFilter
 */
public class ClaimsHeaderTestBuilder implements RequestPostProcessor {

  private static final String DEFAULT_USER_ID = "usr_test123";
  private static final List<String> DEFAULT_ROLES = List.of("USER");
  private static final List<String> DEFAULT_PERMISSIONS =
      List.of("transactions:read", "accounts:read", "budgets:read");

  private static final List<String> ADMIN_PERMISSIONS =
      List.of(
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

  private String userId;
  private List<String> roles;
  private List<String> permissions;

  private ClaimsHeaderTestBuilder() {
    this.userId = DEFAULT_USER_ID;
    this.roles = DEFAULT_ROLES;
    this.permissions = DEFAULT_PERMISSIONS;
  }

  /**
   * Creates a default test user with standard claims.
   *
   * <p>Default claims:
   *
   * <ul>
   *   <li>User ID: "usr_test123"
   *   <li>Roles: ["USER"]
   *   <li>Permissions: ["transactions:read", "accounts:read", "budgets:read"]
   * </ul>
   *
   * @return builder configured as default user (also usable as RequestPostProcessor)
   */
  public static ClaimsHeaderTestBuilder defaultUser() {
    return new ClaimsHeaderTestBuilder();
  }

  /**
   * Creates a builder for a custom user.
   *
   * @param userId the user ID (e.g., "usr_abc123")
   * @return builder for method chaining
   */
  public static ClaimsHeaderTestBuilder user(String userId) {
    var builder = new ClaimsHeaderTestBuilder();
    builder.userId = userId;
    return builder;
  }

  /**
   * Creates a builder for an admin user with full permissions.
   *
   * <p>Admin defaults:
   *
   * <ul>
   *   <li>User ID: "usr_admin456"
   *   <li>Roles: ["ADMIN"]
   *   <li>Permissions: all CRUD permissions for transactions, accounts, budgets, users, roles,
   *       audit, currencies, and statement formats
   * </ul>
   *
   * @return builder configured as admin user
   */
  public static ClaimsHeaderTestBuilder admin() {
    var builder = new ClaimsHeaderTestBuilder();
    builder.userId = "usr_admin456";
    builder.roles = List.of("ADMIN");
    builder.permissions = ADMIN_PERMISSIONS;
    return builder;
  }

  /**
   * Sets the permissions for this test user.
   *
   * @param permissions permission values (e.g., "transactions:read", "accounts:write")
   * @return this builder for method chaining
   */
  public ClaimsHeaderTestBuilder withPermissions(String... permissions) {
    this.permissions = List.of(permissions);
    return this;
  }

  /**
   * Sets the roles for this test user.
   *
   * @param roles role values (e.g., "ADMIN", "USER")
   * @return this builder for method chaining
   */
  public ClaimsHeaderTestBuilder withRoles(String... roles) {
    this.roles = List.of(roles);
    return this;
  }

  /**
   * Builds a map of claims headers suitable for unit tests.
   *
   * @return map of header name to header value
   */
  public Map<String, String> buildHeaders() {
    var headers = new LinkedHashMap<String, String>();
    headers.put(ClaimsHeaderAuthenticationFilter.X_USER_ID_HEADER, userId);
    if (!permissions.isEmpty()) {
      headers.put(
          ClaimsHeaderAuthenticationFilter.X_PERMISSIONS_HEADER, String.join(",", permissions));
    }
    if (!roles.isEmpty()) {
      headers.put(ClaimsHeaderAuthenticationFilter.X_ROLES_HEADER, String.join(",", roles));
    }
    return headers;
  }

  /**
   * Adds claims headers to the MockMvc request.
   *
   * @param request the mock request to modify
   * @return the modified request
   */
  @Override
  public MockHttpServletRequest postProcessRequest(MockHttpServletRequest request) {
    request.addHeader(ClaimsHeaderAuthenticationFilter.X_USER_ID_HEADER, userId);
    if (!permissions.isEmpty()) {
      request.addHeader(
          ClaimsHeaderAuthenticationFilter.X_PERMISSIONS_HEADER, String.join(",", permissions));
    }
    if (!roles.isEmpty()) {
      request.addHeader(ClaimsHeaderAuthenticationFilter.X_ROLES_HEADER, String.join(",", roles));
    }
    return request;
  }

  /**
   * Builds a collection of Spring Security authorities from comma-separated permission and role
   * strings.
   *
   * <p>Useful for {@code @WebMvcTest} tests that need explicit authority lists.
   *
   * @param commaSeparatedPermissions comma-separated permissions (e.g., "transactions:read,
   *     accounts:read")
   * @param commaSeparatedRoles comma-separated roles (e.g., "ADMIN, USER")
   * @return collection of granted authorities
   */
  public static Collection<GrantedAuthority> authoritiesFor(
      String commaSeparatedPermissions, String commaSeparatedRoles) {
    List<GrantedAuthority> authorities = new ArrayList<>();

    if (commaSeparatedPermissions != null && !commaSeparatedPermissions.isBlank()) {
      for (var permission : commaSeparatedPermissions.split(",")) {
        var trimmed = permission.trim();
        if (!trimmed.isEmpty()) {
          authorities.add(new SimpleGrantedAuthority(trimmed));
        }
      }
    }

    if (commaSeparatedRoles != null && !commaSeparatedRoles.isBlank()) {
      for (var role : commaSeparatedRoles.split(",")) {
        var trimmed = role.trim();
        if (!trimmed.isEmpty()) {
          authorities.add(new SimpleGrantedAuthority("ROLE_" + trimmed));
        }
      }
    }

    return authorities;
  }
}

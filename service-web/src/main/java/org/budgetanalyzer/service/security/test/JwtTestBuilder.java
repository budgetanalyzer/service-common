package org.budgetanalyzer.service.security.test;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Fluent builder for creating test JWT tokens matching the gateway JWT shape.
 *
 * <p><b>WARNING: FOR TESTING ONLY</b> - This builder creates mock JWTs for integration tests. Do
 * NOT use in production code.
 *
 * <p>Provides a readable, type-safe API for constructing JWTs in tests with different users, roles,
 * permissions, and custom claims. Default JWT shape matches the session-gateway internal JWT
 * format.
 *
 * <p><b>Usage Examples:</b>
 *
 * <pre>{@code
 * // Default test user with basic permissions
 * Jwt jwt = JwtTestBuilder.defaultJwt();
 *
 * // Custom user with specific permissions
 * var jwt = JwtTestBuilder.user("usr_john123")
 *     .withPermissions("transactions:read", "transactions:write")
 *     .withRoles("USER")
 *     .build();
 *
 * // Admin user with all permissions
 * var jwt = JwtTestBuilder.admin().build();
 *
 * // Custom claims for advanced scenarios
 * var jwt = JwtTestBuilder.user("usr_test123")
 *     .withClaim("organization_id", "org-123")
 *     .withClaim("email", "test@example.com")
 *     .build();
 * }</pre>
 *
 * <p><b>Thread Safety:</b> This builder is NOT thread-safe. Create a new instance per test.
 *
 * @see TestSecurityConfig
 */
public class JwtTestBuilder {

  private static final String DEFAULT_SUBJECT = "usr_test123";
  private static final String DEFAULT_ISSUER = "session-gateway";
  private static final String DEFAULT_IDP_SUB = "idp|test-user-idp-sub";
  private static final List<String> DEFAULT_ROLES = List.of("USER");
  private static final List<String> DEFAULT_PERMISSIONS =
      List.of("transactions:read", "accounts:read", "budgets:read");

  private String subject;
  private String issuer;
  private String idpSub;
  private List<String> roles;
  private List<String> permissions;
  private String scope;
  private String audience;
  private Instant issuedAt;
  private Instant expiresAt;
  private final Map<String, Object> customClaims;

  private JwtTestBuilder() {
    this.subject = DEFAULT_SUBJECT;
    this.issuer = DEFAULT_ISSUER;
    this.idpSub = DEFAULT_IDP_SUB;
    this.roles = DEFAULT_ROLES;
    this.permissions = DEFAULT_PERMISSIONS;
    this.issuedAt = Instant.now();
    this.expiresAt = Instant.now().plusSeconds(3600); // 1 hour
    this.customClaims = new HashMap<>();
  }

  /**
   * Creates a default test JWT with standard gateway claims.
   *
   * <p>Default claims:
   *
   * <ul>
   *   <li>Subject: "usr_test123"
   *   <li>Issuer: "session-gateway"
   *   <li>IDP Sub: "idp|test-user-idp-sub"
   *   <li>Roles: ["USER"]
   *   <li>Permissions: ["transactions:read", "accounts:read", "budgets:read"]
   *   <li>Issued: now
   *   <li>Expires: 1 hour from now
   * </ul>
   *
   * @return default test JWT
   */
  public static Jwt defaultJwt() {
    return new JwtTestBuilder().build();
  }

  /**
   * Creates a builder for a custom user.
   *
   * @param subject the user subject (internal user ID, e.g., "usr_abc123")
   * @return builder for method chaining
   */
  public static JwtTestBuilder user(String subject) {
    return new JwtTestBuilder().withSubject(subject);
  }

  /**
   * Creates a builder for an admin user with full permissions.
   *
   * <p>Admin defaults:
   *
   * <ul>
   *   <li>Subject: "usr_admin456"
   *   <li>Roles: ["ADMIN"]
   *   <li>Permissions: all CRUD permissions for transactions, accounts, budgets, users, roles, and
   *       audit
   * </ul>
   *
   * @return builder for method chaining
   */
  public static JwtTestBuilder admin() {
    return new JwtTestBuilder()
        .withSubject("usr_admin456")
        .withRoles("ADMIN")
        .withPermissions(
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
            "audit:read");
  }

  /**
   * Sets the subject (user identifier) claim.
   *
   * @param subject the user subject
   * @return this builder for method chaining
   */
  public JwtTestBuilder withSubject(String subject) {
    this.subject = subject;
    return this;
  }

  /**
   * Sets the scope claim as a space-separated string.
   *
   * <p>Scopes are only included in the JWT when explicitly set via this method. Gateway JWTs do not
   * include scopes by default; this method exists for backwards compatibility with tests that
   * require scope-based assertions.
   *
   * @param scopes individual scope values (e.g., "read:rates", "write:rates")
   * @return this builder for method chaining
   */
  public JwtTestBuilder withScopes(String... scopes) {
    this.scope = Arrays.stream(scopes).collect(Collectors.joining(" "));
    return this;
  }

  /**
   * Sets the roles claim as a list.
   *
   * <p>Roles are mapped to Spring Security authorities with "ROLE_" prefix (e.g., "ADMIN" becomes
   * "ROLE_ADMIN").
   *
   * @param roles role values (e.g., "ADMIN", "USER")
   * @return this builder for method chaining
   */
  public JwtTestBuilder withRoles(String... roles) {
    this.roles = List.of(roles);
    return this;
  }

  /**
   * Sets the permissions claim as a list.
   *
   * <p>Permissions are mapped directly to Spring Security authorities (e.g., "transactions:read"
   * becomes a {@code SimpleGrantedAuthority("transactions:read")}).
   *
   * @param permissions permission values (e.g., "transactions:read", "accounts:write")
   * @return this builder for method chaining
   */
  public JwtTestBuilder withPermissions(String... permissions) {
    this.permissions = List.of(permissions);
    return this;
  }

  /**
   * Sets the idp_sub (identity provider subject) claim.
   *
   * <p>This is the original subject from the identity provider, preserved for backwards
   * compatibility and IdP-specific operations.
   *
   * @param idpSub the identity provider subject (e.g., "idp|507f1f77...")
   * @return this builder for method chaining
   */
  public JwtTestBuilder withIdpSub(String idpSub) {
    this.idpSub = idpSub;
    return this;
  }

  /**
   * Sets the issuer (iss) claim.
   *
   * @param issuer the token issuer
   * @return this builder for method chaining
   */
  public JwtTestBuilder withIssuer(String issuer) {
    this.issuer = issuer;
    return this;
  }

  /**
   * Sets the audience (aud) claim.
   *
   * <p>Audience is only included in the JWT when explicitly set via this method. Gateway JWTs do
   * not include an audience claim; this method exists for backwards compatibility.
   *
   * @param audience the intended audience
   * @return this builder for method chaining
   */
  public JwtTestBuilder withAudience(String audience) {
    this.audience = audience;
    return this;
  }

  /**
   * Sets the issued-at (iat) claim.
   *
   * @param issuedAt the token issue time
   * @return this builder for method chaining
   */
  public JwtTestBuilder withIssuedAt(Instant issuedAt) {
    this.issuedAt = issuedAt;
    return this;
  }

  /**
   * Sets the expiration (exp) claim.
   *
   * @param expiresAt the token expiration time
   * @return this builder for method chaining
   */
  public JwtTestBuilder withExpiresAt(Instant expiresAt) {
    this.expiresAt = expiresAt;
    return this;
  }

  /**
   * Adds a custom claim to the JWT.
   *
   * <p>Useful for testing scenarios that require additional claims beyond the standard ones (sub,
   * iss, idp_sub, roles, permissions, iat, exp).
   *
   * @param name the claim name
   * @param value the claim value
   * @return this builder for method chaining
   */
  public JwtTestBuilder withClaim(String name, Object value) {
    this.customClaims.put(name, value);
    return this;
  }

  /**
   * Builds the JWT with all configured claims.
   *
   * @return the constructed JWT
   */
  public Jwt build() {
    var builder =
        Jwt.withTokenValue("test-token")
            .header("alg", "RS256")
            .header("typ", "JWT")
            .claim("sub", subject)
            .claim("iss", issuer)
            .claim("idp_sub", idpSub)
            .claim("roles", roles)
            .claim("permissions", permissions)
            .issuedAt(issuedAt)
            .expiresAt(expiresAt);

    // Only include scope if explicitly set (gateway JWTs don't have scopes)
    if (scope != null) {
      builder.claim("scope", scope);
    }

    // Only include audience if explicitly set (gateway JWTs don't have audience)
    if (audience != null) {
      builder.claim("aud", audience);
    }

    // Add any custom claims
    customClaims.forEach(builder::claim);

    return builder.build();
  }
}

package org.budgetanalyzer.service.security;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;

/**
 * OAuth2 Resource Server security configuration for all service-web consumers.
 *
 * <p>This auto-configuration provides:
 *
 * <ul>
 *   <li>JWT token validation against the session-gateway JWKS endpoint
 *   <li>User identity extraction from JWT 'sub' claim (internal user ID)
 *   <li>Permission extraction from JWT 'permissions' claim (direct authorities)
 *   <li>Role extraction from JWT 'roles' claim (ROLE_-prefixed authorities)
 *   <li>Method-level security with @PreAuthorize annotations
 * </ul>
 *
 * <p><b>Usage:</b> All services consuming service-web automatically inherit this configuration.
 * Services must configure {@code spring.security.oauth2.resourceserver.jwt.jwk-set-uri} in their
 * application.yml pointing to the session-gateway's JWKS endpoint.
 *
 * <p><b>Testing:</b> In tests, use {@link
 * org.budgetanalyzer.service.security.test.TestSecurityConfig} with {@code @Import} to provide a
 * mock JWT decoder.
 *
 * @see SecurityContextUtil
 * @see org.budgetanalyzer.service.security.test.TestSecurityConfig
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@SuppressWarnings("checkstyle:AbbreviationAsWordInName") // OAuth2 is standard industry term
public class OAuth2ResourceServerSecurityConfig {

  private static final Logger logger =
      LoggerFactory.getLogger(OAuth2ResourceServerSecurityConfig.class);

  @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}")
  private String jwkSetUri;

  @Value("${budgetanalyzer.security.gateway.expected-issuer:session-gateway}")
  private String expectedIssuer;

  @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}")
  private String legacyIssuerUri;

  /**
   * Configures HTTP security with OAuth2 Resource Server JWT validation.
   *
   * <p>Security policy:
   *
   * <ul>
   *   <li>Actuator health endpoints: Permit all (for load balancer health checks)
   *   <li>OpenAPI documentation endpoints: Permit all (for API docs generation)
   *   <li>All other endpoints: Require authentication
   * </ul>
   *
   * @param http the HttpSecurity to configure
   * @return the configured SecurityFilterChain
   * @throws Exception if configuration fails
   */
  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    logger.info("Configuring OAuth2 Resource Server security");

    http.authorizeHttpRequests(
            authorize ->
                authorize
                    // Allow actuator health endpoint (for load balancer health checks)
                    .requestMatchers("/actuator/health", "/actuator/health/**")
                    .permitAll()
                    // Allow OpenAPI documentation endpoints (for API docs generation)
                    .requestMatchers(
                        "/v3/api-docs",
                        "/v3/api-docs/**",
                        "/v3/api-docs.yaml",
                        "/*/v3/api-docs",
                        "/*/v3/api-docs/**",
                        "/*/v3/api-docs.yaml",
                        "/swagger-ui/**",
                        "/swagger-ui.html")
                    .permitAll()
                    // All other requests require authentication
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            oauth2 ->
                oauth2
                    .authenticationEntryPoint(
                        (request, response, authException) -> {
                          logger.error("=== Authentication Failed ===");
                          logger.error("Request URI: {}", request.getRequestURI());
                          logger.error(
                              "Authorization header present: {}",
                              request.getHeader("Authorization") != null);

                          if (request.getHeader("Authorization") != null) {
                            var authHeader = request.getHeader("Authorization");
                            logger.error(
                                "Authorization header starts with Bearer: {}",
                                authHeader.startsWith("Bearer "));

                            if (authHeader.startsWith("Bearer ")) {
                              var token = authHeader.substring(7);
                              logger.error("Token length: {}", token.length());

                              // Log first 50 chars of token for debugging
                              logger.error(
                                  "Token preview: {}...",
                                  token.length() > 50 ? token.substring(0, 50) : token);
                            }
                          }

                          logger.error("Authentication exception: {}", authException.getMessage());
                          logger.error("Exception type: {}", authException.getClass().getName());

                          if (authException.getCause() != null) {
                            logger.error("Caused by: {}", authException.getCause().getMessage());
                            logger.error(
                                "Root cause type: {}",
                                authException.getCause().getClass().getName());
                          }

                          // Default behavior - return 401
                          response.setStatus(401);
                          response.setContentType("application/json");
                          response
                              .getWriter()
                              .write(
                                  "{\"error\":\"Unauthorized\",\"message\":\""
                                      + authException.getMessage()
                                      + "\"}");
                        })
                    .jwt(
                        jwt ->
                            jwt.decoder(jwtDecoder())
                                .jwtAuthenticationConverter(jwtAuthenticationConverter())));

    return http.build();
  }

  /**
   * Configures JWT decoder to validate gateway-minted JWTs via JWKS endpoint.
   *
   * <p>This decoder:
   *
   * <ul>
   *   <li>Fetches JWKS from the session-gateway's well-known endpoint
   *   <li>Validates issuer matches the expected gateway issuer
   *   <li>Accepts both "JWT" and "at+jwt" token types (OAuth 2.0 RFC 9068)
   * </ul>
   *
   * <p>A migration guard detects if the legacy {@code issuer-uri} property is still configured and
   * fails fast with clear instructions, preventing cryptic validation failures during lockstep
   * upgrades.
   *
   * <p>This bean is only created if no other {@code JwtDecoder} bean exists. In tests, import
   * {@link org.budgetanalyzer.service.security.test.TestSecurityConfig} which provides a
   * {@code @Primary} mock decoder, preventing this production decoder from being created.
   *
   * @return configured JwtDecoder
   */
  @Bean
  @ConditionalOnMissingBean(JwtDecoder.class)
  public JwtDecoder jwtDecoder() {
    // Migration guard: fail fast if legacy issuer-uri is still configured
    if (legacyIssuerUri != null && !legacyIssuerUri.isBlank()) {
      logger.error(
          "=== MIGRATION REQUIRED === "
              + "Property 'spring.security.oauth2.resourceserver.jwt.issuer-uri' is still set. "
              + "This property is no longer used. Replace with: "
              + "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=<gateway-jwks-url> "
              + "(e.g., http://session-gateway:8081/.well-known/jwks.json)");
      throw new IllegalStateException(
          "Legacy property 'spring.security.oauth2.resourceserver.jwt.issuer-uri' is still "
              + "configured. Replace with 'spring.security.oauth2.resourceserver.jwt.jwk-set-uri' "
              + "pointing to the session-gateway JWKS endpoint.");
    }

    logger.info("=== JWT Decoder Configuration ===");
    logger.info("JWK Set URI: {}", jwkSetUri);
    logger.info("Expected issuer: {}", expectedIssuer);

    try {
      // Create decoder using JWK Set URI (gateway has no OIDC discovery endpoint)
      // Configure to accept both "JWT" and "at+jwt" token types (OAuth 2.0 RFC 9068)
      var decoder =
          NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
              .jwtProcessorCustomizer(
                  jwtProcessor ->
                      jwtProcessor.setJWSTypeVerifier(
                          new DefaultJOSEObjectTypeVerifier<>(
                              new JOSEObjectType("at+jwt"), JOSEObjectType.JWT)))
              .build();

      logger.info("JWT decoder created successfully (JWKS will be fetched on first use)");
      logger.info("JWT decoder configured to accept token types: JWT, at+jwt");

      // Add issuer validation
      decoder.setJwtValidator(
          token -> {
            logger.debug("=== JWT Validation ===");
            logger.debug("Token issuer (iss claim): {}", token.getClaimAsString("iss"));
            logger.debug("Token subject: {}", token.getSubject());
            logger.debug("Token algorithm: {}", token.getHeaders().get("alg"));
            logger.debug("Token kid: {}", token.getHeaders().get("kid"));
            logger.debug("Token expiration: {}", token.getExpiresAt());
            logger.debug("Token issued at: {}", token.getIssuedAt());

            // Validate issuer — use getClaimAsString("iss") because gateway issuer
            // "session-gateway" is not a valid URL, and getIssuer() calls getClaimAsURL()
            var issuer = token.getClaimAsString("iss");
            if (issuer == null || !issuer.equals(expectedIssuer)) {
              logger.error(
                  "JWT validation failed: Token issuer '{}' does not match expected '{}'",
                  issuer,
                  expectedIssuer);

              var error =
                  new OAuth2Error("invalid_token", "Token issuer does not match expected", null);

              return OAuth2TokenValidatorResult.failure(error);
            }

            logger.debug("JWT validation successful");
            return OAuth2TokenValidatorResult.success();
          });

      logger.info("JWT decoder configured successfully");
      logger.info("JWKS endpoint: {}", jwkSetUri);

      return decoder;

    } catch (Exception e) {
      logger.error("=== JWT Decoder Configuration Failed ===");
      logger.error("Failed to configure JWT decoder", e);
      logger.error("JWK Set URI was: {}", jwkSetUri);
      logger.error("Exception type: {}", e.getClass().getName());

      if (e.getCause() != null) {
        logger.error("Caused by: {}", e.getCause().getMessage());
        logger.error("Root cause type: {}", e.getCause().getClass().getName());
      }

      throw new IllegalStateException("JWT decoder configuration failed", e);
    }
  }

  /**
   * Converts JWT claims to Spring Security authorities.
   *
   * <p>Extracts authorities from gateway JWT claims:
   *
   * <ul>
   *   <li>{@code permissions} claim (list) mapped to direct authorities (e.g., "transactions:read"
   *       becomes {@code SimpleGrantedAuthority("transactions:read")})
   *   <li>{@code roles} claim (list) mapped to ROLE_-prefixed authorities (e.g., "ADMIN" becomes
   *       {@code SimpleGrantedAuthority("ROLE_ADMIN")})
   * </ul>
   *
   * <p>This enables {@code @PreAuthorize("hasAuthority('transactions:read')")} and
   * {@code @PreAuthorize("hasRole('ADMIN')")} in consuming services.
   *
   * @return configured JwtAuthenticationConverter
   */
  @Bean
  public JwtAuthenticationConverter jwtAuthenticationConverter() {
    var jwtAuthenticationConverter = new JwtAuthenticationConverter();

    jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(
        jwt -> {
          List<GrantedAuthority> authorities = new ArrayList<>();

          // Extract permissions → direct authorities
          var permissions = jwt.getClaimAsStringList("permissions");
          if (permissions != null) {
            for (var permission : permissions) {
              authorities.add(new SimpleGrantedAuthority(permission));
            }
          }

          // Extract roles → ROLE_-prefixed authorities
          var roles = jwt.getClaimAsStringList("roles");
          if (roles != null) {
            for (var role : roles) {
              authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
            }
          }

          return authorities;
        });

    logger.debug(
        "JWT authentication converter configured with permission and role-based authorities");

    return jwtAuthenticationConverter;
  }
}

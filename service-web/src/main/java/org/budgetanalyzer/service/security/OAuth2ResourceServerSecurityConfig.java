package org.budgetanalyzer.service.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;

/**
 * OAuth2 Resource Server security configuration for all service-web consumers.
 *
 * <p>This auto-configuration provides:
 *
 * <ul>
 *   <li>JWT token validation from Auth0 (via NGINX gateway)
 *   <li>User identity extraction from JWT 'sub' claim
 *   <li>Role/scope extraction from JWT claims
 *   <li>Method-level security with @PreAuthorize annotations
 * </ul>
 *
 * <p><b>Usage:</b> All services consuming service-web automatically inherit this configuration.
 * Services must configure {@code spring.security.oauth2.resourceserver.jwt.issuer-uri} and {@code
 * AUTH0_AUDIENCE} in their application.yml.
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

  @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}")
  private String issuerUri;

  @Value("${AUTH0_AUDIENCE:https://api.budgetanalyzer.org}")
  private String audience;

  /**
   * Configures HTTP security with OAuth2 Resource Server JWT validation.
   *
   * <p>Security policy:
   *
   * <ul>
   *   <li>Actuator health endpoints: Permit all (for load balancer health checks)
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
   * Configures JWT decoder with support for PS256, RS256, and ES256 algorithms.
   *
   * <p>This decoder:
   *
   * <ul>
   *   <li>Fetches JWKS from Auth0's well-known endpoint
   *   <li>Validates issuer matches configured issuer URI
   *   <li>Validates audience matches configured API audience
   *   <li>Accepts both "JWT" and "at+jwt" token types (OAuth 2.0 RFC 9068)
   *   <li>Supports PS256, RS256, and ES256 signature algorithms
   * </ul>
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
    logger.info("=== JWT Decoder Configuration ===");
    logger.info("Issuer URI: {}", issuerUri);
    logger.info("Expected audience: {}", audience);

    try {
      logger.info(
          "Attempting to fetch OIDC configuration from: {}/.well-known/openid-configuration",
          issuerUri);

      // Create decoder using issuer URI (fetches JWKS from .well-known/openid-configuration)
      // Configure to accept both "JWT" and "at+jwt" token types (OAuth 2.0 RFC 9068)
      var decoder =
          NimbusJwtDecoder.withIssuerLocation(issuerUri)
              .jwtProcessorCustomizer(
                  jwtProcessor ->
                      jwtProcessor.setJWSTypeVerifier(
                          new DefaultJOSEObjectTypeVerifier<>(
                              new JOSEObjectType("at+jwt"), JOSEObjectType.JWT)))
              .build();

      logger.info("JWT decoder created successfully (JWKS will be fetched on first use)");
      logger.info("JWT decoder configured to accept token types: JWT, at+jwt");

      // Add audience validation
      decoder.setJwtValidator(
          token -> {
            logger.debug("=== JWT Validation ===");
            logger.debug("Token issuer: {}", token.getIssuer());
            logger.debug("Token audience: {}", token.getAudience());
            logger.debug("Token subject: {}", token.getSubject());
            logger.debug("Token algorithm: {}", token.getHeaders().get("alg"));
            logger.debug("Token kid: {}", token.getHeaders().get("kid"));
            logger.debug("Token expiration: {}", token.getExpiresAt());
            logger.debug("Token issued at: {}", token.getIssuedAt());
            logger.debug("All token headers: {}", token.getHeaders());
            logger.debug("All token claims: {}", token.getClaims());

            // Validate audience
            if (token.getAudience() == null || token.getAudience().isEmpty()) {
              logger.error("JWT validation failed: Token has no audience claim");

              var error = new OAuth2Error("invalid_token", "Token must have an audience", null);

              return OAuth2TokenValidatorResult.failure(error);
            }

            var audienceMatches = token.getAudience().contains(audience);
            if (!audienceMatches) {
              logger.error(
                  "JWT validation failed: Token audience {} does not match expected audience {}",
                  token.getAudience(),
                  audience);

              var error = new OAuth2Error("invalid_token", "Token audience does not match", null);

              return OAuth2TokenValidatorResult.failure(error);
            }

            logger.debug("JWT validation successful");
            return OAuth2TokenValidatorResult.success();
          });

      logger.info("JWT decoder configured successfully");
      logger.info("Decoder will accept tokens with algorithms: PS256, RS256, ES256");
      logger.info("JWKS endpoint: {}/.well-known/jwks.json", issuerUri);
      logger.info("OIDC configuration endpoint: {}/.well-known/openid-configuration", issuerUri);

      return decoder;

    } catch (Exception e) {
      logger.error("=== JWT Decoder Configuration Failed ===");
      logger.error("Failed to configure JWT decoder", e);
      logger.error("Issuer URI was: {}", issuerUri);
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
   * <p>Extracts roles/scopes from the JWT and maps them to Spring Security authorities:
   *
   * <ul>
   *   <li>Scopes from 'scope' claim (space-delimited string, e.g., "openid profile email")
   *   <li>Each scope prefixed with "SCOPE_" (e.g., "openid" → "SCOPE_openid")
   * </ul>
   *
   * @return configured JwtAuthenticationConverter
   */
  @Bean
  public JwtAuthenticationConverter jwtAuthenticationConverter() {
    var grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();

    // Extract authorities from 'scope' claim (default: space-delimited string)
    grantedAuthoritiesConverter.setAuthoritiesClaimName("scope");
    grantedAuthoritiesConverter.setAuthorityPrefix("SCOPE_");

    var jwtAuthenticationConverter = new JwtAuthenticationConverter();
    jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);

    logger.debug("JWT authentication converter configured with scope-based authorities");

    return jwtAuthenticationConverter;
  }
}

package org.budgetanalyzer.service.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security configuration for claims-header-based authentication via Envoy ext_authz.
 *
 * <p>Replaces the previous OAuth2 Resource Server configuration. Backend services no longer
 * validate JWTs — they trust pre-validated headers from the infrastructure (spoofing prevented by
 * Envoy stripping incoming headers + mTLS).
 *
 * <p>This auto-configuration provides:
 *
 * <ul>
 *   <li>Claims header extraction via {@link ClaimsHeaderAuthenticationFilter}
 *   <li>Permission-based authorization via {@code @PreAuthorize("hasAuthority('...')")}
 *   <li>Role-based authorization via {@code @PreAuthorize("hasRole('...')")}
 *   <li>Method-level security with {@code @EnableMethodSecurity}
 * </ul>
 *
 * <p><b>Usage:</b> All services consuming service-web automatically inherit this configuration. No
 * properties are required — authentication is performed by Envoy ext_authz before requests reach
 * the service.
 *
 * @see ClaimsHeaderAuthenticationFilter
 * @see ClaimsHeaderAuthenticationToken
 * @see SecurityContextUtil
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@ConditionalOnWebApplication(type = Type.SERVLET)
@ConditionalOnClass(name = "jakarta.servlet.Filter")
public class ClaimsHeaderSecurityConfig {

  private static final Logger logger = LoggerFactory.getLogger(ClaimsHeaderSecurityConfig.class);

  /**
   * Configures HTTP security with claims-header-based authentication.
   *
   * <p>Security policy:
   *
   * <ul>
   *   <li>Actuator health endpoints: Permit all (for load balancer health checks)
   *   <li>Internal service-to-service endpoints ({@code /internal/**}): Permit all (secured at
   *       network level, not exposed through the gateway)
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
    logger.info("Configuring claims-header-based security (Envoy ext_authz)");

    http.csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(
            authorize ->
                authorize
                    .requestMatchers("/actuator/health", "/actuator/health/**")
                    .permitAll()
                    .requestMatchers("/internal/**")
                    .permitAll()
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
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(
            new ClaimsHeaderAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
        .exceptionHandling(
            exceptions ->
                exceptions.authenticationEntryPoint(
                    (request, response, authException) -> {
                      logger.debug(
                          "Authentication failed for {}: {}",
                          request.getRequestURI(),
                          authException.getMessage());
                      response.setStatus(401);
                      response.setContentType("application/json");
                      response.getWriter().write("{\"error\":\"Unauthorized\"}");
                    }));

    return http.build();
  }
}

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
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.NullSecurityContextRepository;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Security configuration for claims-header-based authentication.
 *
 * <p>Backend services do not validate JWTs locally. They trust canonical claims headers injected by
 * the trusted ingress external-auth path.
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
 * properties are required — authentication is performed before requests reach the service.
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
   *   <li>OpenAPI documentation endpoints: Permit all (for API docs generation)
   *   <li>All other endpoints: Require authentication
   * </ul>
   *
   * @param http the HttpSecurity to configure
   * @param objectMapper the ObjectMapper used for JSON security responses
   * @return the configured SecurityFilterChain
   * @throws Exception if configuration fails
   */
  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper)
      throws Exception {
    logger.info("Configuring stateless claims-header security");

    http.csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .securityContext(
            securityContext ->
                securityContext.securityContextRepository(new NullSecurityContextRepository()))
        .authorizeHttpRequests(
            authorize ->
                authorize
                    .requestMatchers("/actuator/health", "/actuator/health/**")
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
            new ClaimsHeaderAuthenticationFilter(objectMapper),
            UsernamePasswordAuthenticationFilter.class)
        .exceptionHandling(
            exceptions ->
                exceptions
                    .authenticationEntryPoint(
                        (request, response, authException) -> {
                          logger.debug(
                              "Authentication failed for {}: {}",
                              request.getRequestURI(),
                              authException.getMessage());
                          ClaimsHeaderSecurityErrorResponseWriter.writeUnauthorized(
                              response, objectMapper);
                        })
                    .accessDeniedHandler(
                        (request, response, accessDeniedException) -> {
                          logger.debug(
                              "Authorization failed for {}: {}",
                              request.getRequestURI(),
                              accessDeniedException.getMessage());
                          ClaimsHeaderSecurityErrorResponseWriter.writeForbidden(
                              response, objectMapper);
                        }));

    return http.build();
  }
}

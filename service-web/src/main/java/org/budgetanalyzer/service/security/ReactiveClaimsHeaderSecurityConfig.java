package org.budgetanalyzer.service.security;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;

import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Mono;

/**
 * Reactive security configuration for claims-header-based authentication.
 *
 * <p>This auto-configuration mirrors the servlet claims-header contract for WebFlux backend
 * services, including reactive {@code @PreAuthorize} support, while backing off when an application
 * defines its own {@link SecurityWebFilterChain}.
 */
@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity(useAuthorizationManager = true)
@ConditionalOnWebApplication(type = Type.REACTIVE)
@ConditionalOnClass(name = "org.springframework.web.server.WebFilter")
public class ReactiveClaimsHeaderSecurityConfig {

  private static final Logger logger =
      LoggerFactory.getLogger(ReactiveClaimsHeaderSecurityConfig.class);

  /**
   * Configures stateless WebFlux security using trusted claims headers.
   *
   * @param http the ServerHttpSecurity to configure
   * @param objectMapper the ObjectMapper used for JSON security responses
   * @return the configured SecurityWebFilterChain
   */
  @Bean
  @ConditionalOnMissingBean(SecurityWebFilterChain.class)
  public SecurityWebFilterChain securityWebFilterChain(
      ServerHttpSecurity http, ObjectMapper objectMapper) {
    logger.info("Configuring stateless reactive claims-header security");

    ReactiveAuthenticationManager reactiveAuthenticationManager =
        authentication -> Mono.just(authentication);
    var authenticationWebFilter = new AuthenticationWebFilter(reactiveAuthenticationManager);
    authenticationWebFilter.setAuthenticationFailureHandler(
        (webFilterExchange, exception) ->
            ReactiveClaimsHeaderSecurityErrorResponseWriter.writeUnauthorized(
                webFilterExchange.getExchange(), objectMapper));
    authenticationWebFilter.setServerAuthenticationConverter(
        exchange -> {
          var headers = exchange.getRequest().getHeaders();
          var userId = headers.getFirst(ClaimsHeaderAuthenticationFilter.X_USER_ID_HEADER);
          var permissionsHeader =
              headers.getFirst(ClaimsHeaderAuthenticationFilter.X_PERMISSIONS_HEADER);
          var rolesHeader = headers.getFirst(ClaimsHeaderAuthenticationFilter.X_ROLES_HEADER);

          if (!ClaimsHeaderValidator.hasAnyClaimsHeaders(userId, permissionsHeader, rolesHeader)) {
            return Mono.empty();
          }

          try {
            var validatedClaimsHeaders =
                ClaimsHeaderValidator.validate(userId, permissionsHeader, rolesHeader);
            return Mono.just(toAuthentication(validatedClaimsHeaders));
          } catch (ClaimsHeaderValidationException exception) {
            logger.debug(
                "Rejecting malformed reactive claims headers for {}: {}",
                exchange.getRequest().getURI().getPath(),
                exception.getMessage());
            return Mono.error(new BadCredentialsException(exception.getMessage(), exception));
          }
        });
    authenticationWebFilter.setSecurityContextRepository(
        NoOpServerSecurityContextRepository.getInstance());

    return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
        .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
        .authorizeExchange(
            exchange ->
                exchange
                    .pathMatchers("/actuator/health", "/actuator/health/**")
                    .permitAll()
                    .pathMatchers(
                        "/v3/api-docs",
                        "/v3/api-docs/**",
                        "/v3/api-docs.yaml",
                        "/*/v3/api-docs",
                        "/*/v3/api-docs/**",
                        "/*/v3/api-docs.yaml",
                        "/swagger-ui/**",
                        "/swagger-ui.html")
                    .permitAll()
                    .anyExchange()
                    .authenticated())
        .addFilterAt(authenticationWebFilter, SecurityWebFiltersOrder.AUTHENTICATION)
        .exceptionHandling(
            exceptionHandling ->
                exceptionHandling
                    .authenticationEntryPoint(
                        (exchange, authenticationException) ->
                            ReactiveClaimsHeaderSecurityErrorResponseWriter.writeUnauthorized(
                                exchange, objectMapper))
                    .accessDeniedHandler(
                        (exchange, accessDeniedException) ->
                            ReactiveClaimsHeaderSecurityErrorResponseWriter.writeForbidden(
                                exchange, objectMapper)))
        .build();
  }

  private static ClaimsHeaderAuthenticationToken toAuthentication(
      ValidatedClaimsHeaders validatedClaimsHeaders) {
    List<GrantedAuthority> authorities = new ArrayList<>();
    for (var permission : validatedClaimsHeaders.permissions()) {
      authorities.add(new SimpleGrantedAuthority(permission));
    }
    for (var role : validatedClaimsHeaders.roles()) {
      authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
    }

    return new ClaimsHeaderAuthenticationToken(
        validatedClaimsHeaders.userId(),
        new LinkedHashSet<>(validatedClaimsHeaders.roles()),
        authorities);
  }
}

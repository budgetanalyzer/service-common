package org.budgetanalyzer.service.permission.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.budgetanalyzer.service.permission.AuthorizationContext;

/**
 * Context information included in authorization requests for audit logging.
 *
 * @param clientIp the client IP address
 * @param correlationId the request correlation ID for tracing
 * @param sourceService the name of the service making the request
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthorizeRequestContext(String clientIp, String correlationId, String sourceService) {

  /**
   * Creates a request context from an AuthorizationContext.
   *
   * @param ctx the authorization context
   * @return a new AuthorizeRequestContext
   */
  public static AuthorizeRequestContext from(AuthorizationContext ctx) {
    return new AuthorizeRequestContext(ctx.clientIp(), ctx.correlationId(), ctx.sourceService());
  }
}

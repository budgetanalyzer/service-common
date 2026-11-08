package org.budgetanalyzer.service.servlet.audit;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import org.budgetanalyzer.service.audit.AuditLogger;
import org.budgetanalyzer.service.security.SecurityContextUtil;
import org.budgetanalyzer.service.servlet.http.CorrelationIdFilter;

/** Emits one audit event after each mapped state-changing servlet request completes. */
public class AuditRequestInterceptor implements HandlerInterceptor {

  private static final Set<String> AUDITED_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
  private static final String RECORDED_ATTRIBUTE =
      AuditRequestInterceptor.class.getName() + ".recorded";

  private final AuditLogger auditLogger;

  /**
   * Creates the interceptor.
   *
   * @param auditLogger shared structured audit logger
   */
  public AuditRequestInterceptor(AuditLogger auditLogger) {
    this.auditLogger = auditLogger;
  }

  @Override
  public void afterCompletion(
      HttpServletRequest request,
      HttpServletResponse response,
      Object handler,
      Exception exception) {
    if (!(handler instanceof HandlerMethod)
        || !AUDITED_METHODS.contains(request.getMethod())
        || Boolean.TRUE.equals(request.getAttribute(RECORDED_ATTRIBUTE))) {
      return;
    }

    request.setAttribute(RECORDED_ATTRIBUTE, Boolean.TRUE);
    auditLogger.log(
        SecurityContextUtil.getCurrentUserId().orElse("anonymous"),
        request.getMethod() + " " + routePattern(request),
        resourceIdentifier(request),
        response.getStatus(),
        correlationId(response));
  }

  private static String routePattern(HttpServletRequest request) {
    var routePattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    return routePattern == null ? "" : routePattern.toString();
  }

  private static String resourceIdentifier(HttpServletRequest request) {
    var pathVariables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
    if (!(pathVariables instanceof Map<?, ?> variables)) {
      return "";
    }
    return variables.values().stream().map(Object::toString).collect(Collectors.joining("/"));
  }

  private static String correlationId(HttpServletResponse response) {
    var correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
    if (correlationId != null) {
      return correlationId;
    }
    var responseCorrelationId = response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
    return responseCorrelationId == null ? "" : responseCorrelationId;
  }
}

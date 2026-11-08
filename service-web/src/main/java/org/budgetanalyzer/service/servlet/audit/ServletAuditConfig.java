package org.budgetanalyzer.service.servlet.audit;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import org.budgetanalyzer.service.audit.AuditLogger;

/** Registers automatic audit events for mapped state-changing Spring MVC requests. */
@Configuration
public class ServletAuditConfig implements WebMvcConfigurer {

  private final AuditRequestInterceptor auditRequestInterceptor;

  /**
   * Creates the servlet audit configuration.
   *
   * @param auditLogger shared structured audit logger
   */
  public ServletAuditConfig(AuditLogger auditLogger) {
    this.auditRequestInterceptor = new AuditRequestInterceptor(auditLogger);
  }

  @Override
  public void addInterceptors(InterceptorRegistry interceptorRegistry) {
    interceptorRegistry.addInterceptor(auditRequestInterceptor);
  }
}

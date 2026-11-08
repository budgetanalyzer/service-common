package org.budgetanalyzer.service.audit;

import java.time.Instant;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.databind.json.JsonMapper;

/** Emits structured audit events to the dedicated {@code budgetanalyzer.audit} logger. */
public class AuditLogger {

  /** Logger name for routing audit events to a separately controlled log destination. */
  public static final String LOGGER_NAME = "budgetanalyzer.audit";

  private static final Logger AUDIT_LOGGER = LoggerFactory.getLogger(LOGGER_NAME);
  private static final JsonMapper AUDIT_JSON_MAPPER = JsonMapper.builder().build();

  /**
   * Creates and emits an audit event at INFO.
   *
   * @param actor authenticated internal user identifier, or {@code anonymous}
   * @param operation operation name or HTTP method and route pattern
   * @param resourceIdentifier resource identifier, or an empty string
   * @param outcome HTTP response status code
   * @param correlationId request correlation identifier, or an empty string
   */
  public void log(
      String actor,
      String operation,
      String resourceIdentifier,
      int outcome,
      String correlationId) {
    log(
        new AuditEvent(
            Instant.now(), actor, operation, resourceIdentifier, outcome, correlationId));
  }

  /**
   * Emits an existing audit event as one compact JSON log entry at INFO.
   *
   * @param auditEvent event to emit
   */
  public void log(AuditEvent auditEvent) {
    Objects.requireNonNull(auditEvent, "auditEvent must not be null");
    try {
      AUDIT_LOGGER.info("{}", AUDIT_JSON_MAPPER.writeValueAsString(auditEvent));
    } catch (Exception exception) {
      AUDIT_LOGGER.error("Failed to serialize audit event", exception);
    }
  }
}

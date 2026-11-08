package org.budgetanalyzer.service.audit;

import java.time.Instant;
import java.util.Objects;

/**
 * Structured record of a state-changing service operation.
 *
 * <p>The event deliberately contains no request or response payload, query parameters, or user
 * profile attributes. {@code resourceIdentifier} is derived from route path variables and is empty
 * when the route has none.
 *
 * @param timestamp time at which the event was emitted
 * @param actor authenticated internal user identifier, or {@code anonymous}
 * @param operation HTTP method and route pattern
 * @param resourceIdentifier slash-separated route path-variable values, or an empty string
 * @param outcome HTTP response status code
 * @param correlationId request correlation identifier, or an empty string
 */
public record AuditEvent(
    Instant timestamp,
    String actor,
    String operation,
    String resourceIdentifier,
    int outcome,
    String correlationId) {

  /** Validates the required audit fields. */
  public AuditEvent {
    Objects.requireNonNull(timestamp, "timestamp must not be null");
    Objects.requireNonNull(actor, "actor must not be null");
    Objects.requireNonNull(operation, "operation must not be null");
    Objects.requireNonNull(resourceIdentifier, "resourceIdentifier must not be null");
    Objects.requireNonNull(correlationId, "correlationId must not be null");
  }
}

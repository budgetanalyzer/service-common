package org.budgetanalyzer.core.logging;

import java.util.UUID;

/**
 * Utility for generating correlation IDs for distributed tracing.
 *
 * <p>Generates unique correlation IDs in the format: {@code req_{32-hex-chars}} (36 chars total).
 * This follows the ecosystem-wide prefixed ID convention ({@code {prefix}_{uuid-hex}}) used for all
 * cross-service identifiers (e.g., {@code usr_}, {@code txn_}, {@code req_}).
 *
 * <p>Prefixed string IDs are used instead of auto-increment longs because these identifiers flow
 * across service boundaries and into JWTs. Auto-increment longs couple identity to a single
 * database; string UUIDs are portable by default. Prefixes make IDs self-describing in logs, JWTs,
 * and database queries.
 *
 * <p>Full UUIDs (32 hex chars, 128 bits of entropy) are used without truncation. Truncation reduces
 * entropy with no storage benefit since the storage type is {@code String} either way.
 */
public final class CorrelationIdGenerator {

  private static final String CORRELATION_ID_PREFIX = "req_";

  private CorrelationIdGenerator() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * Generates a new correlation ID.
   *
   * <p>Format: {@code req_{32-hex-chars}} (36 chars total, 128 bits of entropy).
   *
   * @return correlation ID string
   */
  public static String generate() {
    var uuid = UUID.randomUUID().toString().replace("-", "");
    return CORRELATION_ID_PREFIX + uuid;
  }
}

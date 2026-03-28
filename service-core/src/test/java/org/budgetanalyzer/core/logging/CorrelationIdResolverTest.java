package org.budgetanalyzer.core.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CorrelationIdResolverTest {

  @Test
  void shouldPreserveValidInboundCorrelationId() {
    var correlationId = CorrelationIdResolver.resolveOrGenerate("req_valid-123:abc.DEF");

    assertThat(correlationId).isEqualTo("req_valid-123:abc.DEF");
  }

  @Test
  void shouldTrimWhitespaceAroundValidInboundCorrelationId() {
    var correlationId = CorrelationIdResolver.resolveOrGenerate("  req_valid-123  ");

    assertThat(correlationId).isEqualTo("req_valid-123");
  }

  @Test
  void shouldGenerateCorrelationIdWhenInboundValueIsBlank() {
    var correlationId = CorrelationIdResolver.resolveOrGenerate("   ");

    assertThat(correlationId).startsWith("req_").hasSize(36);
  }

  @Test
  void shouldGenerateCorrelationIdWhenInboundValueContainsUnsafeCharacters() {
    var correlationId = CorrelationIdResolver.resolveOrGenerate("req invalid");

    assertThat(correlationId).startsWith("req_").hasSize(36);
    assertThat(correlationId).isNotEqualTo("req invalid");
  }

  @Test
  void shouldGenerateCorrelationIdWhenInboundValueContainsControlCharacters() {
    var correlationId = CorrelationIdResolver.resolveOrGenerate("req_invalid\nvalue");

    assertThat(correlationId).startsWith("req_").hasSize(36);
  }

  @Test
  void shouldGenerateCorrelationIdWhenInboundValueContainsNonAsciiCharacters() {
    var correlationId = CorrelationIdResolver.resolveOrGenerate("req_☃");

    assertThat(correlationId).startsWith("req_").hasSize(36);
  }

  @Test
  void shouldGenerateCorrelationIdWhenInboundValueExceedsMaxLength() {
    var correlationId = CorrelationIdResolver.resolveOrGenerate("a".repeat(129));

    assertThat(correlationId).startsWith("req_").hasSize(36);
  }
}

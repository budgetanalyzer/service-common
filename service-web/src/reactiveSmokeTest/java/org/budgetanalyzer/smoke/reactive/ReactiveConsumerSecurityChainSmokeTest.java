package org.budgetanalyzer.smoke.reactive;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Verifies over HTTP that the shared reactive claims-header chain backs off when a consumer defines
 * its own {@link SecurityWebFilterChain}, while reactive method security keeps enforcing
 * {@code @PreAuthorize}.
 */
@SpringBootTest(
    classes = ReactiveSmokeApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Import(ReactiveConsumerSecurityChainSmokeTest.ConsumerSecurityConfig.class)
@DisplayName("Reactive consumer-defined security chain smoke tests")
class ReactiveConsumerSecurityChainSmokeTest {

  @Autowired private ApplicationContext applicationContext;

  @Autowired private WebTestClient webTestClient;

  @Test
  @DisplayName("Should replace the shared chain with the consumer chain")
  void shouldReplaceSharedChainWithConsumerChain() {
    assertThat(applicationContext.containsBean("consumerSecurityWebFilterChain")).isTrue();
    assertThat(applicationContext.containsBean("securityWebFilterChain")).isFalse();

    webTestClient
        .get()
        .uri("/api/smoke/items/item_1")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.id")
        .isEqualTo("item_1");
  }

  @Test
  @DisplayName("Should still enforce reactive @PreAuthorize under the consumer chain")
  void shouldStillEnforceReactivePreAuthorize() {
    webTestClient
        .post()
        .uri("/api/smoke/items")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"name\":\"New item\"}")
        .exchange()
        .expectStatus()
        .isForbidden()
        .expectBody()
        .jsonPath("$.type")
        .isEqualTo("FORBIDDEN");
  }

  @TestConfiguration
  static class ConsumerSecurityConfig {

    @Bean
    SecurityWebFilterChain consumerSecurityWebFilterChain(ServerHttpSecurity http) {
      return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
          .authorizeExchange(exchange -> exchange.anyExchange().permitAll())
          .build();
    }
  }
}

package org.budgetanalyzer.smoke.servlet;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Verifies over HTTP that a consumer-defined servlet security chain takes precedence over the
 * shared claims-header chain for the requests it matches.
 *
 * <p>The shared servlet chain is registered at {@code BASIC_AUTH_ORDER} and coexists with consumer
 * chains; a consumer chain with a lower order is evaluated first.
 */
@SpringBootTest(
    classes = ServletSmokeApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(ServletConsumerSecurityChainSmokeTest.ConsumerSecurityConfig.class)
@DisplayName("Servlet consumer-defined security chain smoke tests")
class ServletConsumerSecurityChainSmokeTest {

  @Autowired private ApplicationContext applicationContext;

  @Autowired private RestTestClient restTestClient;

  @Test
  @DisplayName("Should apply the consumer chain to its matched requests")
  void shouldApplyConsumerChainToMatchedRequests() {
    assertThat(applicationContext.containsBean("consumerSecurityFilterChain")).isTrue();

    restTestClient
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
  @DisplayName("Should keep the shared chain for requests the consumer chain does not match")
  void shouldKeepSharedChainForUnmatchedRequests() {
    restTestClient
        .get()
        .uri("/api/smoke/business-error")
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectBody()
        .jsonPath("$.type")
        .isEqualTo("UNAUTHORIZED");
  }

  @TestConfiguration
  static class ConsumerSecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain consumerSecurityFilterChain(HttpSecurity http) throws Exception {
      return http.securityMatcher("/api/smoke/items/**")
          .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
          .build();
    }
  }
}

package org.budgetanalyzer.service.integration;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.MethodNotAllowedException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.WebFilter;

import reactor.core.publisher.Mono;

import org.budgetanalyzer.service.security.test.ClaimsHeaderTestBuilder;

/** Integration tests verifying reactive filter-level failures use the shared API error contract. */
@SpringBootTest(
    classes = {
      ReactiveTestApplication.class,
      ReactiveErrorWebExceptionHandlerIntegrationTest.FilterLevelExceptionTestConfiguration.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {"spring.main.web-application-type=reactive"})
@AutoConfigureWebTestClient
@DisplayName("Reactive Error Web Exception Handler Integration Tests")
class ReactiveErrorWebExceptionHandlerIntegrationTest {

  @Autowired private WebTestClient webTestClient;

  @Test
  @DisplayName("Should render ApiErrorResponse for generic filter-level failures")
  void shouldRenderApiErrorResponseForGenericFilterLevelFailures() {
    webTestClient
        .get()
        .uri("/api/filter-errors/runtime")
        .headers(
            headers -> ClaimsHeaderTestBuilder.defaultUser().buildHeaders().forEach(headers::add))
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
        .expectBody()
        .jsonPath("$.type")
        .isEqualTo("INTERNAL_ERROR")
        .jsonPath("$.message")
        .isEqualTo("An unexpected error occurred");
  }

  @Test
  @DisplayName("Should render ApiErrorResponse for filter-level ResponseStatusException")
  void shouldRenderApiErrorResponseForFilterLevelResponseStatusException() {
    webTestClient
        .get()
        .uri("/api/filter-errors/not-found")
        .headers(
            headers -> ClaimsHeaderTestBuilder.defaultUser().buildHeaders().forEach(headers::add))
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
        .expectBody()
        .jsonPath("$.type")
        .isEqualTo("NOT_FOUND")
        .jsonPath("$.message")
        .isEqualTo("Filter resource not found");
  }

  @Test
  @DisplayName("Should preserve protocol headers for filter-level ResponseStatusException")
  void shouldPreserveProtocolHeadersForFilterLevelResponseStatusException() {
    webTestClient
        .post()
        .uri("/api/filter-errors/method-not-allowed")
        .headers(
            headers -> ClaimsHeaderTestBuilder.defaultUser().buildHeaders().forEach(headers::add))
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.METHOD_NOT_ALLOWED)
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
        .expectHeader()
        .valueMatches(HttpHeaders.ALLOW, ".*GET.*PUT.*")
        .expectBody()
        .jsonPath("$.type")
        .isEqualTo("INVALID_REQUEST");
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class FilterLevelExceptionTestConfiguration {

    @Bean
    WebFilter filterLevelExceptionWebFilter() {
      return (exchange, chain) -> {
        var path = exchange.getRequest().getPath().value();
        if ("/api/filter-errors/runtime".equals(path)) {
          return Mono.error(new IllegalStateException("Filter failure"));
        }
        if ("/api/filter-errors/not-found".equals(path)) {
          return Mono.error(
              new ResponseStatusException(HttpStatus.NOT_FOUND, "Filter resource not found"));
        }
        if ("/api/filter-errors/method-not-allowed".equals(path)) {
          return Mono.error(
              new MethodNotAllowedException(
                  HttpMethod.POST, List.of(HttpMethod.GET, HttpMethod.PUT)));
        }
        return chain.filter(exchange);
      };
    }

    @Bean
    FilterLevelExceptionTestController filterLevelExceptionTestController() {
      return new FilterLevelExceptionTestController();
    }
  }

  @RestController
  @RequestMapping("/api/filter-errors")
  static class FilterLevelExceptionTestController {

    /**
     * Fallback endpoint for generic filter-level error testing.
     *
     * @return ok response when the filter does not fail first
     */
    @GetMapping("/runtime")
    public Mono<ResponseEntity<String>> runtimeError() {
      return Mono.just(ResponseEntity.ok("runtime-ok"));
    }

    /**
     * Fallback endpoint for response-status filter-level error testing.
     *
     * @return ok response when the filter does not fail first
     */
    @GetMapping("/not-found")
    public Mono<ResponseEntity<String>> notFoundError() {
      return Mono.just(ResponseEntity.ok("not-found-ok"));
    }

    /**
     * Fallback endpoint for filter-level method-not-allowed testing.
     *
     * @return ok response when the filter does not fail first
     */
    @PostMapping("/method-not-allowed")
    public Mono<ResponseEntity<String>> methodNotAllowedError() {
      return Mono.just(ResponseEntity.ok("method-not-allowed-ok"));
    }
  }
}

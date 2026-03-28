package org.budgetanalyzer.service.integration.fixture;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;

/** Test reactive controller for method-security integration tests. */
@RestController
@RequestMapping("/api/reactive-security")
public class ReactiveMethodSecurityTestController {

  /**
   * Endpoint protected by method-level permission checks.
   *
   * @return ok response for authorized callers
   */
  @GetMapping("/transactions-write")
  @PreAuthorize("hasAuthority('transactions:write')")
  public Mono<ResponseEntity<String>> transactionsWriteProtectedEndpoint() {
    return Mono.just(ResponseEntity.ok("method-security-ok"));
  }
}

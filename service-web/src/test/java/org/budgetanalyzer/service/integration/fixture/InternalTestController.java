package org.budgetanalyzer.service.integration.fixture;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Test REST controller for internal endpoint security integration tests. */
@RestController
@RequestMapping("/internal/test")
public class InternalTestController {

  /**
   * Simple endpoint for testing internal path security rules.
   *
   * @return ok response
   */
  @GetMapping
  public ResponseEntity<String> internalEndpoint() {
    return ResponseEntity.ok("internal-ok");
  }
}

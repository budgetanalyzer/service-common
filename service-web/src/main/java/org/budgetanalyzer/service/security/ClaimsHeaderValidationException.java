package org.budgetanalyzer.service.security;

class ClaimsHeaderValidationException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  ClaimsHeaderValidationException(String message) {
    super(message);
  }
}

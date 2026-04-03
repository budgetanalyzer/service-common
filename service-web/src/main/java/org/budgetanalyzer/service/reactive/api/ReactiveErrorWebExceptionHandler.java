package org.budgetanalyzer.service.reactive.api;

import java.io.IOException;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;

import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Mono;

import org.budgetanalyzer.service.api.ApiErrorType;
import org.budgetanalyzer.service.api.ApiExceptionHandler;

/**
 * Reactive fallback exception handler for filter-level WebFlux failures.
 *
 * <p>This handler renders the shared JSON {@code ApiErrorResponse} contract for exceptions that
 * occur before controller advice can handle them, such as failures raised from reactive filters.
 *
 * <p>Services can override this default by defining their own {@link ErrorWebExceptionHandler}
 * bean.
 */
public class ReactiveErrorWebExceptionHandler
    implements ErrorWebExceptionHandler, Ordered, ApiExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ReactiveErrorWebExceptionHandler.class);
  private static final int ERROR_HANDLER_ORDER = -1;

  private final ObjectMapper objectMapper;

  /**
   * Creates the reactive fallback exception handler.
   *
   * @param objectMapper object mapper used to serialize {@code ApiErrorResponse}
   */
  public ReactiveErrorWebExceptionHandler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public int getOrder() {
    return ERROR_HANDLER_ORDER;
  }

  @Override
  public Mono<Void> handle(ServerWebExchange exchange, Throwable throwable) {
    var response = exchange.getResponse();
    if (response.isCommitted()) {
      return Mono.error(throwable);
    }

    var resolvedError = resolveError(throwable);
    logResolvedError(
        throwable, resolvedError.response().getType(), resolvedError.response().getCode());

    byte[] body;
    try {
      body = objectMapper.writeValueAsBytes(resolvedError.response());
    } catch (IOException exception) {
      return Mono.error(exception);
    }

    response.setStatusCode(resolvedError.statusCode());
    response.getHeaders().addAll(resolvedError.headers());
    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
    return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
  }

  private ResolvedError resolveError(Throwable throwable) {
    if (throwable instanceof WebExchangeBindException exception) {
      return resolveValidationFailure(exception.getBindingResult());
    }
    return resolveCommonException(throwable);
  }

  private void logResolvedError(Throwable throwable, ApiErrorType type, String code) {
    var message = throwable.getMessage();
    var rootCause = ExceptionUtils.getRootCause(throwable);
    if (rootCause != null) {
      log.warn(
          "Handled reactive web exception type: {} code: {} exception: {} "
              + "root cause: {} message: {}",
          type,
          code,
          throwable.getClass(),
          rootCause.getClass(),
          message,
          throwable);
      return;
    }

    log.warn(
        "Handled reactive web exception type: {} code: {} exception: {} message: {}",
        type,
        code,
        throwable.getClass(),
        message,
        throwable);
  }
}

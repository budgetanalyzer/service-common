package com.bleurubin.service.api;

import java.time.Instant;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import com.bleurubin.service.exception.BusinessException;
import com.bleurubin.service.exception.InvalidRequestException;
import com.bleurubin.service.exception.ResourceNotFoundException;
import com.bleurubin.service.exception.ServiceUnavailableException;

/*
 * This will just be the default handler with lowest precedence.  Any components
 * with @RestControllerAdvice will take higher precedence and can override the
 * default handling.  The goal is to present common html status and error messages
 * across all services to make it easier for clients to parse.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class DefaultApiExceptionHandler {

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  @ExceptionHandler
  @ResponseStatus(value = HttpStatus.BAD_REQUEST)
  public ApiErrorResponse handle(InvalidRequestException exception, WebRequest request) {
    return handleApiException("invalid_request", HttpStatus.BAD_REQUEST, request, exception);
  }

  @ExceptionHandler
  @ResponseStatus(value = HttpStatus.NOT_FOUND)
  public ApiErrorResponse handle(ResourceNotFoundException exception, WebRequest request) {
    return handleApiException("not_found", HttpStatus.NOT_FOUND, request, exception);
  }

  @ExceptionHandler
  @ResponseStatus(value = HttpStatus.UNPROCESSABLE_ENTITY)
  public ApiErrorResponse handle(BusinessException exception, WebRequest request) {
    return handleApiException(
        "invalid_request", HttpStatus.UNPROCESSABLE_ENTITY, request, exception);
  }

  @ExceptionHandler
  @ResponseStatus(value = HttpStatus.BAD_GATEWAY)
  public ApiErrorResponse handle(ServiceUnavailableException exception, WebRequest request) {
    return handleApiException("internal_server_error", HttpStatus.BAD_GATEWAY, request, exception);
  }

  @ExceptionHandler
  @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
  public ApiErrorResponse handle(Exception exception, WebRequest request) {
    return handleApiException(
        "internal_server_error", HttpStatus.INTERNAL_SERVER_ERROR, request, exception);
  }

  private ApiErrorResponse handleApiException(
      String type, HttpStatus httpStatus, WebRequest request, Throwable throwable) {
    var message = throwable.getMessage();
    var rootCause = ExceptionUtils.getRootCause(throwable);
    if (rootCause != null) {
      log.warn(
          "Handled exception: {} root cause: {} message: {}",
          throwable.getClass(),
          rootCause.getClass(),
          message,
          throwable);
    } else {
      log.warn("Handled exception: {} message: {}", throwable.getClass(), message, throwable);
    }

    return ApiErrorResponse.builder()
        .type(type)
        .title(type)
        .status(httpStatus.value())
        .detail(throwable.getMessage())
        .instance(extractUri(request))
        .timestamp(Instant.now())
        .build();
  }

  private String extractUri(WebRequest request) {
    return request.getDescription(false).replace("uri=", "");
  }
}

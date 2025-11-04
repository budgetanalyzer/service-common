package com.bleurubin.service.api;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import com.bleurubin.service.exception.BusinessException;
import com.bleurubin.service.exception.ClientException;
import com.bleurubin.service.exception.InvalidRequestException;
import com.bleurubin.service.exception.ResourceNotFoundException;
import com.bleurubin.service.exception.ServiceUnavailableException;

/*
 * This will just be the default handler with lowest precedence.  Any components
 * with @RestControllerAdvice will take higher precedence and can override the
 * default handling.  The goal is to present common HTTP status and error messages
 * across all services to make it easier for clients to parse.
 */
@AutoConfiguration
@ConditionalOnWebApplication
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class DefaultApiExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(DefaultApiExceptionHandler.class);

  @ExceptionHandler
  @ResponseStatus(value = HttpStatus.BAD_REQUEST)
  public ApiErrorResponse handle(InvalidRequestException exception, WebRequest request) {
    return handleApiException(ApiErrorType.INVALID_REQUEST, exception);
  }

  @ExceptionHandler
  @ResponseStatus(value = HttpStatus.NOT_FOUND)
  public ApiErrorResponse handle(ResourceNotFoundException exception, WebRequest request) {
    return handleApiException(ApiErrorType.NOT_FOUND, exception);
  }

  @ExceptionHandler
  @ResponseStatus(value = HttpStatus.UNPROCESSABLE_ENTITY)
  public ApiErrorResponse handle(BusinessException exception, WebRequest request) {
    return handleApiException(ApiErrorType.APPLICATION_ERROR, exception.getCode(), exception);
  }

  @ExceptionHandler
  @ResponseStatus(value = HttpStatus.SERVICE_UNAVAILABLE)
  public ApiErrorResponse handle(ClientException exception, WebRequest request) {
    return handleApiException(ApiErrorType.SERVICE_UNAVAILABLE, exception);
  }

  @ExceptionHandler
  @ResponseStatus(value = HttpStatus.SERVICE_UNAVAILABLE)
  public ApiErrorResponse handle(ServiceUnavailableException exception, WebRequest request) {
    return handleApiException(ApiErrorType.SERVICE_UNAVAILABLE, exception);
  }

  @ExceptionHandler
  @ResponseStatus(value = HttpStatus.BAD_REQUEST)
  public ApiErrorResponse handle(
      MethodArgumentTypeMismatchException exception, WebRequest request) {
    return handleApiException(ApiErrorType.INVALID_REQUEST, exception);
  }

  @ExceptionHandler
  @ResponseStatus(value = HttpStatus.BAD_REQUEST)
  public ApiErrorResponse handle(MissingServletRequestPartException exception, WebRequest request) {
    return handleApiException(ApiErrorType.INVALID_REQUEST, exception);
  }

  @ExceptionHandler
  @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
  public ApiErrorResponse handle(Exception exception, WebRequest request) {
    return handleApiException(ApiErrorType.INTERNAL_ERROR, exception);
  }

  private ApiErrorResponse handleApiException(ApiErrorType type, Throwable throwable) {
    return handleApiException(type, null, throwable);
  }

  private ApiErrorResponse handleApiException(ApiErrorType type, String code, Throwable throwable) {
    var message = throwable.getMessage();
    var rootCause = ExceptionUtils.getRootCause(throwable);
    if (rootCause != null) {
      log.warn(
          "Handled exception type: {} code: {} exception: {} root cause: {} message: {}",
          type,
          code,
          throwable.getClass(),
          rootCause.getClass(),
          message,
          throwable);
    } else {
      log.warn(
          "Handled exception type: {} code: {} exception: {} message: {}",
          type,
          code,
          throwable.getClass(),
          message,
          throwable);
    }

    return ApiErrorResponse.builder().type(type).message(message).code(code).build();
  }
}

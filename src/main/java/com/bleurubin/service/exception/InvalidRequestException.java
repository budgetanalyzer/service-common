package com.bleurubin.service.exception;

public class InvalidRequestException extends ServiceException {

  public InvalidRequestException(String message) {
    super(message);
  }

  public InvalidRequestException(String message, Throwable cause) {
    super(message, cause);
  }
}

package com.bleurubin.service.exception;

public class ClientException extends ServiceException {

  public ClientException(String message) {
    super(message);
  }

  public ClientException(String message, Throwable cause) {
    super(message, cause);
  }
}

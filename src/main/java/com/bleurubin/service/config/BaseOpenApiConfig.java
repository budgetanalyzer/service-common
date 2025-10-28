package com.bleurubin.service.config;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;

import com.bleurubin.service.api.ApiErrorResponse;
import com.bleurubin.service.api.ApiErrorType;

/**
 * This class is abstract to prevent direct instantiation because we need the
 * service-specific @Configuration and @OpenApiDefinition annotations to pass service information to
 * SpringDoc.
 */
public abstract class BaseOpenApiConfig {

  @Bean
  public SpringDocConfigProperties springDocConfigProperties() {
    SpringDocConfigProperties props = new SpringDocConfigProperties();
    // this prevents SpringDoc from scanning RestControllerAdvice and
    // adding every possible response to every endpoint
    props.setOverrideWithGenericResponse(false);

    return props;
  }

  @Bean
  public OpenApiCustomizer globalResponseCustomizer() {
    return openApi -> {
      if (openApi.getPaths() != null) {
        openApi
            .getPaths()
            .values()
            .forEach(
                pathItem -> pathItem.readOperationsMap().forEach(this::addStandardErrorResponses));
      }
    };
  }

  protected void addStandardErrorResponses(PathItem.HttpMethod httpMethod, Operation operation) {
    switch (httpMethod) {
      case POST:
        addBadRequestResponse(operation);
        break;

      case PUT:
      case PATCH:
        addBadRequestResponse(operation);
        addNotFoundResponse(operation);
        break;

      case GET:
      case DELETE:
        // We don't return a 404 for a GET without an {id}
        if (hasPathParameters(operation)) {
          addNotFoundResponse(operation);
        }
        break;

      default:
        // No standard error responses for other methods
        break;
    }

    addInternalServerErrorResponse(operation);
    addServiceUnavailableResponse(operation);
  }

  protected ApiResponse buildExampleApiErrorResponse(
      HttpStatus httpStatus, String message, String code) {
    var exampleResponse =
        ApiErrorResponse.builder()
            .type(getTypeFromHttpStatus(httpStatus))
            .message(message)
            .code(code)
            .build();

    return new ApiResponse()
        .description(httpStatus.getReasonPhrase())
        .content(
            new Content()
                .addMediaType(
                    "application/json",
                    new MediaType()
                        .schema(new Schema<>().$ref("#/components/schemas/ApiErrorResponse"))
                        .example(exampleResponse)));
  }

  protected boolean hasPathParameters(Operation operation) {
    return operation.getParameters() != null
        && operation.getParameters().stream().anyMatch(param -> "path".equals(param.getIn()));
  }

  protected void addBadRequestResponse(Operation operation) {
    operation
        .getResponses()
        .addApiResponse("400", buildExampleApiErrorResponse(HttpStatus.BAD_REQUEST));
  }

  protected void addNotFoundResponse(Operation operation) {
    operation
        .getResponses()
        .addApiResponse("404", buildExampleApiErrorResponse(HttpStatus.NOT_FOUND));
  }

  protected void addInternalServerErrorResponse(Operation operation) {
    operation
        .getResponses()
        .addApiResponse("500", buildExampleApiErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR));
  }

  protected void addServiceUnavailableResponse(Operation operation) {
    operation
        .getResponses()
        .addApiResponse("503", buildExampleApiErrorResponse(HttpStatus.SERVICE_UNAVAILABLE));
  }

  private ApiResponse buildExampleApiErrorResponse(HttpStatus httpStatus) {
    return buildExampleApiErrorResponse(httpStatus, httpStatus.getReasonPhrase(), null);
  }

  private ApiErrorType getTypeFromHttpStatus(HttpStatus httpStatus) {
    return switch (httpStatus) {
      case HttpStatus.BAD_REQUEST -> ApiErrorType.INVALID_REQUEST;
      case HttpStatus.NOT_FOUND -> ApiErrorType.NOT_FOUND;
      case HttpStatus.UNPROCESSABLE_ENTITY -> ApiErrorType.APPLICATION_ERROR;
      case HttpStatus.SERVICE_UNAVAILABLE -> ApiErrorType.SERVICE_UNAVAILABLE;
      default -> ApiErrorType.INTERNAL_ERROR;
    };
  }
}

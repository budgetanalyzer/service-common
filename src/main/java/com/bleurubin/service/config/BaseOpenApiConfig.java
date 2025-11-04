package com.bleurubin.service.config;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;

import com.bleurubin.service.api.ApiErrorResponse;
import com.bleurubin.service.api.ApiErrorType;
import com.bleurubin.service.api.FieldError;

/**
 * Base OpenAPI configuration for standardizing API documentation across microservices.
 *
 * <p>This abstract class provides common OpenAPI/Swagger configuration that ensures consistent API
 * documentation and error response schemas across all microservices in the Budget Analyzer
 * ecosystem.
 *
 * <p>This class is abstract to prevent direct instantiation because we need service-specific
 * {@code @Configuration} and {@code @OpenApiDefinition} annotations to pass service information to
 * SpringDoc.
 *
 * <p>Key features:
 *
 * <ul>
 *   <li>Automatic addition of standard error responses (400, 404, 500, 503) to all endpoints
 *   <li>Prevents SpringDoc from scanning RestControllerAdvice (which adds every possible response
 *       to every endpoint)
 *   <li>Ensures ApiErrorResponse and FieldError schemas are included in OpenAPI documentation
 *   <li>Method-specific error response rules (e.g., GET with {id} gets 404, POST gets 400)
 * </ul>
 *
 * <p>Example usage in a microservice:
 *
 * <pre>
 * &#64;Configuration
 * &#64;OpenApiDefinition(
 *     info = &#64;Info(
 *         title = "Budget Analyzer API",
 *         version = "1.0.0",
 *         description = "API for managing financial transactions and budgets"
 *     )
 * )
 * public class OpenApiConfig extends BaseOpenApiConfig {
 * }
 * </pre>
 */
public abstract class BaseOpenApiConfig {

  /**
   * Configures SpringDoc properties to prevent overly generic response documentation.
   *
   * <p>This bean disables {@code overrideWithGenericResponse} which prevents SpringDoc from
   * scanning {@code @RestControllerAdvice} classes and adding every possible exception response to
   * every endpoint. Instead, we explicitly add standard error responses via {@link
   * #globalResponseCustomizer()}.
   *
   * @return configured SpringDoc properties
   */
  @Bean
  public SpringDocConfigProperties springDocConfigProperties() {
    SpringDocConfigProperties props = new SpringDocConfigProperties();
    // this prevents SpringDoc from scanning RestControllerAdvice and
    // adding every possible response to every endpoint
    props.setOverrideWithGenericResponse(false);

    return props;
  }

  /**
   * Creates an OpenAPI customizer that adds standard error responses to all endpoints.
   *
   * <p>This customizer iterates through all paths and operations in the OpenAPI specification and
   * adds appropriate error responses based on the HTTP method (GET, POST, PUT, etc.). Error
   * responses are method-specific:
   *
   * <ul>
   *   <li>POST: 400 Bad Request
   *   <li>PUT/PATCH: 400 Bad Request, 404 Not Found
   *   <li>GET/DELETE with path parameters: 404 Not Found
   *   <li>All methods: 500 Internal Server Error, 503 Service Unavailable
   * </ul>
   *
   * @return an OpenAPI customizer that adds standard error responses
   */
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

  /**
   * Ensures ApiErrorResponse and FieldError schemas are included in the OpenAPI specification.
   *
   * <p>This bean is required for services that never directly reference ApiErrorResponse or
   * FieldError in their controller methods. Without this, SpringDoc would not include these schemas
   * in the generated OpenAPI specification, breaking the standard error response documentation.
   *
   * @return an OpenAPI customizer that adds error response schemas
   */
  @Bean
  public OpenApiCustomizer addApiErrorResponseSchemas() {
    return openApi -> {
      if (openApi.getComponents() == null) {
        openApi.setComponents(new Components());
      }

      var components = openApi.getComponents();
      var converters = ModelConverters.getInstance();

      converters.read(ApiErrorResponse.class).forEach(components::addSchemas);
      converters.read(FieldError.class).forEach(components::addSchemas);
    };
  }

  /**
   * Adds standard error responses to an operation based on its HTTP method.
   *
   * <p>This method applies method-specific error response rules:
   *
   * <ul>
   *   <li>POST: 400 Bad Request (malformed request body)
   *   <li>PUT/PATCH: 400 Bad Request, 404 Not Found (resource doesn't exist)
   *   <li>GET/DELETE with path parameters: 404 Not Found (resource doesn't exist)
   *   <li>GET without path parameters: No 404 (list operations don't fail for empty results)
   *   <li>All methods: 500 Internal Server Error, 503 Service Unavailable
   * </ul>
   *
   * @param httpMethod the HTTP method of the operation (GET, POST, PUT, etc.)
   * @param operation the OpenAPI operation to add error responses to
   */
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

  /**
   * Builds an ApiResponse with an example error response for the given HTTP status.
   *
   * <p>This method creates an OpenAPI response object with:
   *
   * <ul>
   *   <li>A description (the HTTP status reason phrase)
   *   <li>A reference to the ApiErrorResponse schema
   *   <li>An example ApiErrorResponse with the provided message and code
   * </ul>
   *
   * @param httpStatus the HTTP status code for this error response
   * @param message the human-readable error message
   * @param code the machine-readable error code (or null)
   * @return an ApiResponse configured for this error scenario
   */
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

  /**
   * Builds an ApiResponse with a default error message based on the HTTP status.
   *
   * @param httpStatus the HTTP status code for this error response
   * @return an ApiResponse configured for this error scenario
   */
  private ApiResponse buildExampleApiErrorResponse(HttpStatus httpStatus) {
    return buildExampleApiErrorResponse(httpStatus, httpStatus.getReasonPhrase(), null);
  }

  /**
   * Checks if an operation has path parameters (e.g., {id}).
   *
   * <p>This is used to determine whether to add a 404 Not Found response. Operations with path
   * parameters (like GET /transactions/{id}) should return 404 when the resource doesn't exist, but
   * list operations (like GET /transactions) should not.
   *
   * @param operation the operation to check
   * @return true if the operation has at least one path parameter, false otherwise
   */
  protected boolean hasPathParameters(Operation operation) {
    return operation.getParameters() != null
        && operation.getParameters().stream().anyMatch(param -> "path".equals(param.getIn()));
  }

  /**
   * Adds a 400 Bad Request error response to an operation.
   *
   * @param operation the operation to add the response to
   */
  protected void addBadRequestResponse(Operation operation) {
    operation
        .getResponses()
        .addApiResponse("400", buildExampleApiErrorResponse(HttpStatus.BAD_REQUEST));
  }

  /**
   * Adds a 404 Not Found error response to an operation.
   *
   * @param operation the operation to add the response to
   */
  protected void addNotFoundResponse(Operation operation) {
    operation
        .getResponses()
        .addApiResponse("404", buildExampleApiErrorResponse(HttpStatus.NOT_FOUND));
  }

  /**
   * Adds a 500 Internal Server Error response to an operation.
   *
   * @param operation the operation to add the response to
   */
  protected void addInternalServerErrorResponse(Operation operation) {
    operation
        .getResponses()
        .addApiResponse("500", buildExampleApiErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR));
  }

  /**
   * Adds a 503 Service Unavailable error response to an operation.
   *
   * @param operation the operation to add the response to
   */
  protected void addServiceUnavailableResponse(Operation operation) {
    operation
        .getResponses()
        .addApiResponse("503", buildExampleApiErrorResponse(HttpStatus.SERVICE_UNAVAILABLE));
  }

  /**
   * Maps an HTTP status code to the corresponding ApiErrorType.
   *
   * @param httpStatus the HTTP status code
   * @return the corresponding ApiErrorType enum value
   */
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

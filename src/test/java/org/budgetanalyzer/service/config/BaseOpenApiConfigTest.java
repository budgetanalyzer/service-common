package org.budgetanalyzer.service.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponses;

class BaseOpenApiConfigTest {

  private TestOpenApiConfig config;
  private OpenAPI openApi;

  @BeforeEach
  void setUp() {
    config = new TestOpenApiConfig();
    openApi = new OpenAPI();
    openApi.setPaths(new Paths());
    openApi.setComponents(new Components());
  }

  @Test
  void shouldRegisterSpringDocConfigProperties() {
    // Arrange & Act
    try (var context = new AnnotationConfigApplicationContext(TestOpenApiConfig.class)) {
      // Assert
      assertTrue(
          context.containsBean("springDocConfigProperties"),
          "Should register SpringDocConfigProperties bean");
      var props = context.getBean("springDocConfigProperties");
      assertNotNull(props, "SpringDocConfigProperties should not be null");
    }
  }

  @Test
  void shouldRegisterGlobalResponseCustomizer() {
    // Arrange & Act
    try (var context = new AnnotationConfigApplicationContext(TestOpenApiConfig.class)) {
      // Assert
      assertTrue(
          context.containsBean("globalResponseCustomizer"),
          "Should register globalResponseCustomizer bean");
      var customizer = context.getBean("globalResponseCustomizer", OpenApiCustomizer.class);
      assertNotNull(customizer, "OpenApiCustomizer should not be null");
    }
  }

  @Test
  void shouldRegisterApiErrorResponseSchemasCustomizer() {
    // Arrange & Act
    try (var context = new AnnotationConfigApplicationContext(TestOpenApiConfig.class)) {
      // Assert
      assertTrue(
          context.containsBean("addApiErrorResponseSchemas"),
          "Should register addApiErrorResponseSchemas bean");
      var customizer = context.getBean("addApiErrorResponseSchemas", OpenApiCustomizer.class);
      assertNotNull(customizer, "Schema customizer should not be null");
    }
  }

  @Test
  void shouldAddApiErrorResponseSchemasToComponents() {
    // Arrange
    var customizer = config.addApiErrorResponseSchemas();

    // Act
    customizer.customise(openApi);

    // Assert
    assertNotNull(openApi.getComponents(), "Components should not be null");
    var schemas = openApi.getComponents().getSchemas();
    assertNotNull(schemas, "Schemas should not be null");
    assertTrue(schemas.containsKey("ApiErrorResponse"), "Should contain ApiErrorResponse schema");
    assertTrue(schemas.containsKey("FieldError"), "Should contain FieldError schema");
  }

  @Test
  void shouldAddApiErrorResponseSchemasWhenComponentsIsNull() {
    // Arrange
    openApi.setComponents(null);
    var customizer = config.addApiErrorResponseSchemas();

    // Act
    customizer.customise(openApi);

    // Assert
    assertNotNull(openApi.getComponents(), "Components should be created");
    var schemas = openApi.getComponents().getSchemas();
    assertNotNull(schemas, "Schemas should not be null");
    assertTrue(schemas.containsKey("ApiErrorResponse"), "Should contain ApiErrorResponse schema");
    assertTrue(schemas.containsKey("FieldError"), "Should contain FieldError schema");
  }

  @Test
  void shouldAddBadRequestResponseForPostOperation() {
    // Arrange
    var operation = createOperation();
    openApi.getPaths().addPathItem("/api/users", new PathItem().post(operation));
    var customizer = config.globalResponseCustomizer();

    // Act
    customizer.customise(openApi);

    // Assert
    assertTrue(operation.getResponses().containsKey("400"), "POST should have 400 response");
    assertTrue(operation.getResponses().containsKey("500"), "POST should have 500 response");
    assertTrue(operation.getResponses().containsKey("503"), "POST should have 503 response");
    assertFalse(operation.getResponses().containsKey("404"), "POST should NOT have 404 response");
  }

  @Test
  void shouldAddBadRequestAndNotFoundForPutOperation() {
    // Arrange
    var operation = createOperation();
    openApi.getPaths().addPathItem("/api/users/{id}", new PathItem().put(operation));
    var customizer = config.globalResponseCustomizer();

    // Act
    customizer.customise(openApi);

    // Assert
    assertTrue(operation.getResponses().containsKey("400"), "PUT should have 400 response");
    assertTrue(operation.getResponses().containsKey("404"), "PUT should have 404 response");
    assertTrue(operation.getResponses().containsKey("500"), "PUT should have 500 response");
    assertTrue(operation.getResponses().containsKey("503"), "PUT should have 503 response");
  }

  @Test
  void shouldAddBadRequestAndNotFoundForPatchOperation() {
    // Arrange
    var operation = createOperation();
    openApi.getPaths().addPathItem("/api/users/{id}", new PathItem().patch(operation));
    var customizer = config.globalResponseCustomizer();

    // Act
    customizer.customise(openApi);

    // Assert
    assertTrue(operation.getResponses().containsKey("400"), "PATCH should have 400 response");
    assertTrue(operation.getResponses().containsKey("404"), "PATCH should have 404 response");
    assertTrue(operation.getResponses().containsKey("500"), "PATCH should have 500 response");
    assertTrue(operation.getResponses().containsKey("503"), "PATCH should have 503 response");
  }

  @Test
  void shouldAddNotFoundForGetOperationWithPathParameters() {
    // Arrange
    var operation = createOperationWithPathParameter();
    openApi.getPaths().addPathItem("/api/users/{id}", new PathItem().get(operation));
    var customizer = config.globalResponseCustomizer();

    // Act
    customizer.customise(openApi);

    // Assert
    assertTrue(
        operation.getResponses().containsKey("404"),
        "GET with path param should have 404 response");
    assertTrue(
        operation.getResponses().containsKey("500"),
        "GET with path param should have 500 response");
    assertTrue(
        operation.getResponses().containsKey("503"),
        "GET with path param should have 503 response");
    assertFalse(operation.getResponses().containsKey("400"), "GET should NOT have 400 response");
  }

  @Test
  void shouldNotAddNotFoundForGetOperationWithoutPathParameters() {
    // Arrange
    var operation = createOperation(); // No path parameters
    openApi.getPaths().addPathItem("/api/users", new PathItem().get(operation));
    var customizer = config.globalResponseCustomizer();

    // Act
    customizer.customise(openApi);

    // Assert
    assertFalse(
        operation.getResponses().containsKey("404"),
        "GET without path param should NOT have 404 response");
    assertTrue(operation.getResponses().containsKey("500"), "GET should have 500 response");
    assertTrue(operation.getResponses().containsKey("503"), "GET should have 503 response");
    assertFalse(operation.getResponses().containsKey("400"), "GET should NOT have 400 response");
  }

  @Test
  void shouldAddNotFoundForDeleteOperationWithPathParameters() {
    // Arrange
    var operation = createOperationWithPathParameter();
    openApi.getPaths().addPathItem("/api/users/{id}", new PathItem().delete(operation));
    var customizer = config.globalResponseCustomizer();

    // Act
    customizer.customise(openApi);

    // Assert
    assertTrue(
        operation.getResponses().containsKey("404"),
        "DELETE with path param should have 404 response");
    assertTrue(operation.getResponses().containsKey("500"), "DELETE should have 500 response");
    assertTrue(operation.getResponses().containsKey("503"), "DELETE should have 503 response");
    assertFalse(operation.getResponses().containsKey("400"), "DELETE should NOT have 400 response");
  }

  @Test
  void shouldNotAddNotFoundForDeleteOperationWithoutPathParameters() {
    // Arrange
    var operation = createOperation(); // No path parameters
    openApi.getPaths().addPathItem("/api/users", new PathItem().delete(operation));
    var customizer = config.globalResponseCustomizer();

    // Act
    customizer.customise(openApi);

    // Assert
    assertFalse(
        operation.getResponses().containsKey("404"),
        "DELETE without path param should NOT have 404 response");
    assertTrue(operation.getResponses().containsKey("500"), "DELETE should have 500 response");
    assertTrue(operation.getResponses().containsKey("503"), "DELETE should have 503 response");
  }

  @Test
  void shouldAddStandardErrorResponsesToAllOperations() {
    // Arrange
    var getOp = createOperationWithPathParameter();
    var postOp = createOperation();
    var putOp = createOperationWithPathParameter();
    var deleteOp = createOperationWithPathParameter();

    openApi
        .getPaths()
        .addPathItem("/api/users/{id}", new PathItem().get(getOp).put(putOp).delete(deleteOp))
        .addPathItem("/api/users", new PathItem().post(postOp));

    var customizer = config.globalResponseCustomizer();

    // Act
    customizer.customise(openApi);

    // Assert - All operations should have 500 and 503
    assertTrue(getOp.getResponses().containsKey("500"), "GET should have 500");
    assertTrue(getOp.getResponses().containsKey("503"), "GET should have 503");

    assertTrue(postOp.getResponses().containsKey("500"), "POST should have 500");
    assertTrue(postOp.getResponses().containsKey("503"), "POST should have 503");

    assertTrue(putOp.getResponses().containsKey("500"), "PUT should have 500");
    assertTrue(putOp.getResponses().containsKey("503"), "PUT should have 503");

    assertTrue(deleteOp.getResponses().containsKey("500"), "DELETE should have 500");
    assertTrue(deleteOp.getResponses().containsKey("503"), "DELETE should have 503");
  }

  @Test
  void shouldHaveCorrectErrorTypeInBadRequestResponse() {
    // Arrange
    var operation = createOperation();
    openApi.getPaths().addPathItem("/api/users", new PathItem().post(operation));
    var customizer = config.globalResponseCustomizer();

    // Act
    customizer.customise(openApi);

    // Assert
    var response = operation.getResponses().get("400");
    assertNotNull(response, "400 response should exist");
    assertEquals("Bad Request", response.getDescription());

    var content = response.getContent();
    assertNotNull(content, "Response content should not be null");
    assertTrue(content.containsKey("application/json"), "Should have application/json media type");

    var mediaType = content.get("application/json");
    var schema = mediaType.getSchema();
    assertNotNull(schema, "Schema should not be null");
    assertEquals(
        "#/components/schemas/ApiErrorResponse",
        schema.get$ref(),
        "Should reference ApiErrorResponse schema");

    var example = mediaType.getExample();
    assertNotNull(example, "Should have example");
  }

  @Test
  void shouldHaveCorrectErrorTypeInNotFoundResponse() {
    // Arrange
    var operation = createOperationWithPathParameter();
    openApi.getPaths().addPathItem("/api/users/{id}", new PathItem().get(operation));
    var customizer = config.globalResponseCustomizer();

    // Act
    customizer.customise(openApi);

    // Assert
    var response = operation.getResponses().get("404");
    assertNotNull(response, "404 response should exist");
    assertEquals("Not Found", response.getDescription());

    var content = response.getContent();
    assertNotNull(content, "Response content should not be null");
    assertTrue(content.containsKey("application/json"), "Should have application/json media type");

    var mediaType = content.get("application/json");
    var schema = mediaType.getSchema();
    assertNotNull(schema, "Schema should not be null");
    assertEquals(
        "#/components/schemas/ApiErrorResponse",
        schema.get$ref(),
        "Should reference ApiErrorResponse schema");
  }

  @Test
  void shouldHandleEmptyPathsGracefully() {
    // Arrange
    openApi.setPaths(null);
    var customizer = config.globalResponseCustomizer();

    // Act & Assert - Should not throw exception
    customizer.customise(openApi);
  }

  @Test
  void shouldHandleOperationWithoutResponsesGracefully() {
    // Arrange
    var operation = new Operation();
    operation.setResponses(new ApiResponses());
    openApi.getPaths().addPathItem("/api/users", new PathItem().get(operation));
    var customizer = config.globalResponseCustomizer();

    // Act
    customizer.customise(openApi);

    // Assert - Should add standard error responses
    assertTrue(
        operation.getResponses().containsKey("500"),
        "Should add 500 response to operation without responses");
    assertTrue(
        operation.getResponses().containsKey("503"),
        "Should add 503 response to operation without responses");
  }

  @Test
  void shouldDetectPathParametersCorrectly() {
    // Arrange - Operation with path parameter
    var operation = createOperationWithPathParameter();

    // Act
    boolean hasPathParams = config.hasPathParameters(operation);

    // Assert
    assertTrue(hasPathParams, "Should detect path parameter");
  }

  @Test
  void shouldDetectNoPathParametersCorrectly() {
    // Arrange - Operation without path parameters
    var operation = createOperation();

    // Act
    boolean hasPathParams = config.hasPathParameters(operation);

    // Assert
    assertFalse(hasPathParams, "Should detect no path parameters");
  }

  @Test
  void shouldDetectQueryParametersAsNotPathParameters() {
    // Arrange - Operation with query parameter only
    var operation = createOperation();
    var queryParam = new Parameter();
    queryParam.setIn("query");
    queryParam.setName("page");
    operation.addParametersItem(queryParam);

    // Act
    boolean hasPathParams = config.hasPathParameters(operation);

    // Assert
    assertFalse(hasPathParams, "Query parameter should not be detected as path parameter");
  }

  @Test
  void shouldDetectMixedParameters() {
    // Arrange - Operation with both path and query parameters
    final var operation = createOperation();

    var pathParam = new Parameter();
    pathParam.setIn("path");
    pathParam.setName("id");

    var queryParam = new Parameter();
    queryParam.setIn("query");
    queryParam.setName("page");

    operation.addParametersItem(pathParam);
    operation.addParametersItem(queryParam);

    // Act
    boolean hasPathParams = config.hasPathParameters(operation);

    // Assert
    assertTrue(hasPathParams, "Should detect path parameter among mixed parameters");
  }

  @Test
  void shouldBuildExampleApiErrorResponseWithCorrectType() {
    // Arrange & Act
    final var response =
        config.buildExampleApiErrorResponse(
            org.springframework.http.HttpStatus.NOT_FOUND, "Resource not found", "RESOURCE_001");

    // Assert
    assertNotNull(response, "Response should not be null");
    assertEquals("Not Found", response.getDescription());

    var content = response.getContent();
    assertNotNull(content, "Content should not be null");

    var mediaType = content.get("application/json");
    assertNotNull(mediaType, "Media type should not be null");

    var example = mediaType.getExample();
    assertNotNull(example, "Example should not be null");
  }

  @Test
  void shouldHandleMultiplePathsAndOperations() {
    // Arrange
    openApi
        .getPaths()
        .addPathItem("/api/users", new PathItem().get(createOperation()).post(createOperation()))
        .addPathItem(
            "/api/users/{id}",
            new PathItem()
                .get(createOperationWithPathParameter())
                .put(createOperationWithPathParameter())
                .delete(createOperationWithPathParameter()))
        .addPathItem(
            "/api/transactions", new PathItem().get(createOperation()).post(createOperation()))
        .addPathItem(
            "/api/transactions/{id}",
            new PathItem()
                .get(createOperationWithPathParameter())
                .delete(createOperationWithPathParameter()));

    var customizer = config.globalResponseCustomizer();

    // Act
    customizer.customise(openApi);

    // Assert - Verify all operations have appropriate error responses
    var usersPath = openApi.getPaths().get("/api/users");
    assertTrue(usersPath.getGet().getResponses().containsKey("500"), "GET /users should have 500");
    assertTrue(
        usersPath.getPost().getResponses().containsKey("400"), "POST /users should have 400");

    var usersIdPath = openApi.getPaths().get("/api/users/{id}");
    assertTrue(
        usersIdPath.getGet().getResponses().containsKey("404"), "GET /users/{id} should have 404");
    assertTrue(
        usersIdPath.getPut().getResponses().containsKey("400"), "PUT /users/{id} should have 400");
    assertTrue(
        usersIdPath.getPut().getResponses().containsKey("404"), "PUT /users/{id} should have 404");
    assertTrue(
        usersIdPath.getDelete().getResponses().containsKey("404"),
        "DELETE /users/{id} should have 404");
  }

  private Operation createOperation() {
    var operation = new Operation();
    operation.setResponses(new ApiResponses());
    return operation;
  }

  private Operation createOperationWithPathParameter() {
    var operation = createOperation();
    var pathParam = new Parameter();
    pathParam.setIn("path");
    pathParam.setName("id");
    operation.setParameters(new ArrayList<>());
    operation.addParametersItem(pathParam);
    return operation;
  }

  @Configuration
  static class TestOpenApiConfig extends BaseOpenApiConfig {
    // Concrete implementation for testing
  }
}

package org.budgetanalyzer.service.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponses;

class BaseOpenApiConfigTest {

  private TestOpenApiConfig testOpenApiConfig;
  private OpenAPI openApi;

  @BeforeEach
  void setUp() {
    testOpenApiConfig = new TestOpenApiConfig();
    openApi = new OpenAPI();
    openApi.setPaths(new Paths());
    openApi.setComponents(new Components());
  }

  @Test
  void shouldRegisterSpringDocConfigProperties() {
    // Arrange & Act
    try (var context = new AnnotationConfigApplicationContext(TestOpenApiConfig.class)) {
      // Assert
      assertThat(context.containsBean("springDocConfigProperties"))
          .as("Should register SpringDocConfigProperties bean")
          .isTrue();
      var springDocConfigProperties = context.getBean("springDocConfigProperties");
      assertThat(springDocConfigProperties)
          .as("SpringDocConfigProperties should not be null")
          .isNotNull();
    }
  }

  @Test
  void shouldRegisterGlobalResponseCustomizer() {
    // Arrange & Act
    try (var context = new AnnotationConfigApplicationContext(TestOpenApiConfig.class)) {
      // Assert
      assertThat(context.containsBean("globalResponseCustomizer"))
          .as("Should register globalResponseCustomizer bean")
          .isTrue();
      var customizer = context.getBean("globalResponseCustomizer", OpenApiCustomizer.class);
      assertThat(customizer).as("OpenApiCustomizer should not be null").isNotNull();
    }
  }

  @Test
  void shouldRegisterApiErrorResponseSchemasCustomizer() {
    // Arrange & Act
    try (var context = new AnnotationConfigApplicationContext(TestOpenApiConfig.class)) {
      // Assert
      assertThat(context.containsBean("addApiErrorResponseSchemas"))
          .as("Should register addApiErrorResponseSchemas bean")
          .isTrue();
      var customizer = context.getBean("addApiErrorResponseSchemas", OpenApiCustomizer.class);
      assertThat(customizer).as("Schema customizer should not be null").isNotNull();
    }
  }

  @Test
  void shouldAddApiErrorResponseSchemasToComponents() {
    // Arrange
    var customizer = testOpenApiConfig.addApiErrorResponseSchemas();

    // Act
    customizer.customise(openApi);

    // Assert
    assertThat(openApi.getComponents()).as("Components should not be null").isNotNull();
    var schemas = openApi.getComponents().getSchemas();
    assertThat(schemas).as("Schemas should not be null").isNotNull();
    assertThat(schemas.containsKey("ApiErrorResponse"))
        .as("Should contain ApiErrorResponse schema")
        .isTrue();
    assertThat(schemas.containsKey("FieldError")).as("Should contain FieldError schema").isTrue();
  }

  @Test
  void shouldAddApiErrorResponseSchemasWhenComponentsIsNull() {
    // Arrange
    openApi.setComponents(null);
    var customizer = testOpenApiConfig.addApiErrorResponseSchemas();

    // Act
    customizer.customise(openApi);

    // Assert
    assertThat(openApi.getComponents()).as("Components should be created").isNotNull();
    var schemas = openApi.getComponents().getSchemas();
    assertThat(schemas).as("Schemas should not be null").isNotNull();
    assertThat(schemas.containsKey("ApiErrorResponse"))
        .as("Should contain ApiErrorResponse schema")
        .isTrue();
    assertThat(schemas.containsKey("FieldError")).as("Should contain FieldError schema").isTrue();
  }

  @Test
  void shouldAddBadRequestResponseForPostOperation() {
    // Arrange
    var operation = createOperation();
    openApi.getPaths().addPathItem("/api/users", new PathItem().post(operation));
    var customizer = testOpenApiConfig.globalResponseCustomizer();

    // Act
    customizer.customise(openApi);

    // Assert
    assertThat(operation.getResponses().containsKey("400"))
        .as("POST should have 400 response")
        .isTrue();
    assertThat(operation.getResponses().containsKey("500"))
        .as("POST should have 500 response")
        .isTrue();
    assertThat(operation.getResponses().containsKey("503"))
        .as("POST should have 503 response")
        .isTrue();
    assertThat(operation.getResponses().containsKey("404"))
        .as("POST should NOT have 404 response")
        .isFalse();
  }

  @Test
  void shouldAddBadRequestAndNotFoundForPutOperation() {
    // Arrange
    var operation = createOperation();
    openApi.getPaths().addPathItem("/api/users/{id}", new PathItem().put(operation));
    var customizer = testOpenApiConfig.globalResponseCustomizer();

    // Act
    customizer.customise(openApi);

    // Assert
    assertThat(operation.getResponses().containsKey("400"))
        .as("PUT should have 400 response")
        .isTrue();
    assertThat(operation.getResponses().containsKey("404"))
        .as("PUT should have 404 response")
        .isTrue();
    assertThat(operation.getResponses().containsKey("500"))
        .as("PUT should have 500 response")
        .isTrue();
    assertThat(operation.getResponses().containsKey("503"))
        .as("PUT should have 503 response")
        .isTrue();
  }

  @Test
  void shouldAddBadRequestAndNotFoundForPatchOperation() {
    // Arrange
    var operation = createOperation();
    openApi.getPaths().addPathItem("/api/users/{id}", new PathItem().patch(operation));
    var customizer = testOpenApiConfig.globalResponseCustomizer();

    // Act
    customizer.customise(openApi);

    // Assert
    assertThat(operation.getResponses().containsKey("400"))
        .as("PATCH should have 400 response")
        .isTrue();
    assertThat(operation.getResponses().containsKey("404"))
        .as("PATCH should have 404 response")
        .isTrue();
    assertThat(operation.getResponses().containsKey("500"))
        .as("PATCH should have 500 response")
        .isTrue();
    assertThat(operation.getResponses().containsKey("503"))
        .as("PATCH should have 503 response")
        .isTrue();
  }

  @Test
  void shouldAddNotFoundForGetOperationWithPathParameters() {
    // Arrange
    var operation = createOperationWithPathParameter();
    openApi.getPaths().addPathItem("/api/users/{id}", new PathItem().get(operation));
    var customizer = testOpenApiConfig.globalResponseCustomizer();

    // Act
    customizer.customise(openApi);

    // Assert
    assertThat(operation.getResponses().containsKey("404"))
        .as("GET with path param should have 404 response")
        .isTrue();
    assertThat(operation.getResponses().containsKey("500"))
        .as("GET with path param should have 500 response")
        .isTrue();
    assertThat(operation.getResponses().containsKey("503"))
        .as("GET with path param should have 503 response")
        .isTrue();
    assertThat(operation.getResponses().containsKey("400"))
        .as("GET should NOT have 400 response")
        .isFalse();
  }

  @Test
  void shouldNotAddNotFoundForGetOperationWithoutPathParameters() {
    // Arrange
    var operation = createOperation(); // No path parameters
    openApi.getPaths().addPathItem("/api/users", new PathItem().get(operation));
    var customizer = testOpenApiConfig.globalResponseCustomizer();

    // Act
    customizer.customise(openApi);

    // Assert
    assertThat(operation.getResponses().containsKey("404"))
        .as("GET without path param should NOT have 404 response")
        .isFalse();
    assertThat(operation.getResponses().containsKey("500"))
        .as("GET should have 500 response")
        .isTrue();
    assertThat(operation.getResponses().containsKey("503"))
        .as("GET should have 503 response")
        .isTrue();
    assertThat(operation.getResponses().containsKey("400"))
        .as("GET should NOT have 400 response")
        .isFalse();
  }

  @Test
  void shouldAddNotFoundForDeleteOperationWithPathParameters() {
    // Arrange
    var operation = createOperationWithPathParameter();
    openApi.getPaths().addPathItem("/api/users/{id}", new PathItem().delete(operation));
    var customizer = testOpenApiConfig.globalResponseCustomizer();

    // Act
    customizer.customise(openApi);

    // Assert
    assertThat(operation.getResponses().containsKey("404"))
        .as("DELETE with path param should have 404 response")
        .isTrue();
    assertThat(operation.getResponses().containsKey("500"))
        .as("DELETE should have 500 response")
        .isTrue();
    assertThat(operation.getResponses().containsKey("503"))
        .as("DELETE should have 503 response")
        .isTrue();
    assertThat(operation.getResponses().containsKey("400"))
        .as("DELETE should NOT have 400 response")
        .isFalse();
  }

  @Test
  void shouldNotAddNotFoundForDeleteOperationWithoutPathParameters() {
    // Arrange
    var operation = createOperation(); // No path parameters
    openApi.getPaths().addPathItem("/api/users", new PathItem().delete(operation));
    var customizer = testOpenApiConfig.globalResponseCustomizer();

    // Act
    customizer.customise(openApi);

    // Assert
    assertThat(operation.getResponses().containsKey("404"))
        .as("DELETE without path param should NOT have 404 response")
        .isFalse();
    assertThat(operation.getResponses().containsKey("500"))
        .as("DELETE should have 500 response")
        .isTrue();
    assertThat(operation.getResponses().containsKey("503"))
        .as("DELETE should have 503 response")
        .isTrue();
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

    var customizer = testOpenApiConfig.globalResponseCustomizer();

    // Act
    customizer.customise(openApi);

    // Assert - All operations should have 500 and 503
    assertThat(getOp.getResponses().containsKey("500")).as("GET should have 500").isTrue();
    assertThat(getOp.getResponses().containsKey("503")).as("GET should have 503").isTrue();

    assertThat(postOp.getResponses().containsKey("500")).as("POST should have 500").isTrue();
    assertThat(postOp.getResponses().containsKey("503")).as("POST should have 503").isTrue();

    assertThat(putOp.getResponses().containsKey("500")).as("PUT should have 500").isTrue();
    assertThat(putOp.getResponses().containsKey("503")).as("PUT should have 503").isTrue();

    assertThat(deleteOp.getResponses().containsKey("500")).as("DELETE should have 500").isTrue();
    assertThat(deleteOp.getResponses().containsKey("503")).as("DELETE should have 503").isTrue();
  }

  @Test
  void shouldHaveCorrectErrorTypeInBadRequestResponse() {
    // Arrange
    var operation = createOperation();
    openApi.getPaths().addPathItem("/api/users", new PathItem().post(operation));
    var customizer = testOpenApiConfig.globalResponseCustomizer();

    // Act
    customizer.customise(openApi);

    // Assert
    var response = operation.getResponses().get("400");
    assertThat(response).as("400 response should exist").isNotNull();
    assertThat(response.getDescription()).isEqualTo("Bad Request");

    var content = response.getContent();
    assertThat(content).as("Response content should not be null").isNotNull();
    assertThat(content.containsKey("application/json"))
        .as("Should have application/json media type")
        .isTrue();

    var mediaType = content.get("application/json");
    var schema = mediaType.getSchema();
    assertThat(schema).as("Schema should not be null").isNotNull();
    assertThat(schema.get$ref())
        .as("Should reference ApiErrorResponse schema")
        .isEqualTo("#/components/schemas/ApiErrorResponse");

    var example = mediaType.getExample();
    assertThat(example).as("Should have example").isNotNull();
  }

  @Test
  void shouldHaveCorrectErrorTypeInNotFoundResponse() {
    // Arrange
    var operation = createOperationWithPathParameter();
    openApi.getPaths().addPathItem("/api/users/{id}", new PathItem().get(operation));
    var customizer = testOpenApiConfig.globalResponseCustomizer();

    // Act
    customizer.customise(openApi);

    // Assert
    var response = operation.getResponses().get("404");
    assertThat(response).as("404 response should exist").isNotNull();
    assertThat(response.getDescription()).isEqualTo("Not Found");

    var content = response.getContent();
    assertThat(content).as("Response content should not be null").isNotNull();
    assertThat(content.containsKey("application/json"))
        .as("Should have application/json media type")
        .isTrue();

    var mediaType = content.get("application/json");
    var schema = mediaType.getSchema();
    assertThat(schema).as("Schema should not be null").isNotNull();
    assertThat(schema.get$ref())
        .as("Should reference ApiErrorResponse schema")
        .isEqualTo("#/components/schemas/ApiErrorResponse");
  }

  @Test
  void shouldHandleEmptyPathsGracefully() {
    // Arrange
    openApi.setPaths(null);
    var customizer = testOpenApiConfig.globalResponseCustomizer();

    // Act & Assert - Should not throw exception
    customizer.customise(openApi);
  }

  @Test
  void shouldHandleOperationWithoutResponsesGracefully() {
    // Arrange
    var operation = new Operation();
    operation.setResponses(new ApiResponses());
    openApi.getPaths().addPathItem("/api/users", new PathItem().get(operation));
    var customizer = testOpenApiConfig.globalResponseCustomizer();

    // Act
    customizer.customise(openApi);

    // Assert - Should add standard error responses
    assertThat(operation.getResponses().containsKey("500"))
        .as("Should add 500 response to operation without responses")
        .isTrue();
    assertThat(operation.getResponses().containsKey("503"))
        .as("Should add 503 response to operation without responses")
        .isTrue();
  }

  @Test
  void shouldDetectPathParametersCorrectly() {
    // Arrange - Operation with path parameter
    var operation = createOperationWithPathParameter();

    // Act
    boolean hasPathParams = testOpenApiConfig.hasPathParameters(operation);

    // Assert
    assertThat(hasPathParams).as("Should detect path parameter").isTrue();
  }

  @Test
  void shouldDetectNoPathParametersCorrectly() {
    // Arrange - Operation without path parameters
    var operation = createOperation();

    // Act
    boolean hasPathParams = testOpenApiConfig.hasPathParameters(operation);

    // Assert
    assertThat(hasPathParams).as("Should detect no path parameters").isFalse();
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
    boolean hasPathParams = testOpenApiConfig.hasPathParameters(operation);

    // Assert
    assertThat(hasPathParams)
        .as("Query parameter should not be detected as path parameter")
        .isFalse();
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
    boolean hasPathParams = testOpenApiConfig.hasPathParameters(operation);

    // Assert
    assertThat(hasPathParams).as("Should detect path parameter among mixed parameters").isTrue();
  }

  @Test
  void shouldBuildExampleApiErrorResponseWithCorrectType() {
    // Arrange & Act
    final var response =
        testOpenApiConfig.buildExampleApiErrorResponse(
            HttpStatus.NOT_FOUND, "Resource not found", "RESOURCE_001");

    // Assert
    assertThat(response).as("Response should not be null").isNotNull();
    assertThat(response.getDescription()).isEqualTo("Not Found");

    var content = response.getContent();
    assertThat(content).as("Content should not be null").isNotNull();

    var mediaType = content.get("application/json");
    assertThat(mediaType).as("Media type should not be null").isNotNull();

    var example = mediaType.getExample();
    assertThat(example).as("Example should not be null").isNotNull();
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

    var customizer = testOpenApiConfig.globalResponseCustomizer();

    // Act
    customizer.customise(openApi);

    // Assert - Verify all operations have appropriate error responses
    var usersPath = openApi.getPaths().get("/api/users");
    assertThat(usersPath.getGet().getResponses().containsKey("500"))
        .as("GET /users should have 500")
        .isTrue();
    assertThat(usersPath.getPost().getResponses().containsKey("400"))
        .as("POST /users should have 400")
        .isTrue();

    var usersIdPath = openApi.getPaths().get("/api/users/{id}");
    assertThat(usersIdPath.getGet().getResponses().containsKey("404"))
        .as("GET /users/{id} should have 404")
        .isTrue();
    assertThat(usersIdPath.getPut().getResponses().containsKey("400"))
        .as("PUT /users/{id} should have 400")
        .isTrue();
    assertThat(usersIdPath.getPut().getResponses().containsKey("404"))
        .as("PUT /users/{id} should have 404")
        .isTrue();
    assertThat(usersIdPath.getDelete().getResponses().containsKey("404"))
        .as("DELETE /users/{id} should have 404")
        .isTrue();
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

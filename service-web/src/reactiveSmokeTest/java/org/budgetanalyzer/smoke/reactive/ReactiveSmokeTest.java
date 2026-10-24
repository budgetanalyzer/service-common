package org.budgetanalyzer.smoke.reactive;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.reactive.context.ReactiveWebServerApplicationContext;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.ClassUtils;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.budgetanalyzer.service.security.test.ClaimsHeaderTestBuilder;

/**
 * Smoke tests for a reactive consumer on an isolated reactive-only classpath.
 *
 * <p>Requests go over real HTTP to the embedded server, through the shared reactive claims-header
 * security chain, exception handling, actuator, and SpringDoc integration.
 */
@SpringBootTest(
    classes = ReactiveSmokeApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@DisplayName("Reactive consumer smoke tests")
class ReactiveSmokeTest {

  private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

  @Autowired private ApplicationContext applicationContext;

  @Autowired private WebTestClient webTestClient;

  @Test
  @DisplayName("Should start as a reactive web server on a reactive-only classpath")
  void shouldStartAsReactiveWebServerOnReactiveOnlyClasspath() {
    var classLoader = applicationContext.getClassLoader();

    assertThat(applicationContext).isInstanceOf(ReactiveWebServerApplicationContext.class);
    assertThat(
            ClassUtils.isPresent("org.springframework.web.reactive.DispatcherHandler", classLoader))
        .isTrue();
    assertThat(ClassUtils.isPresent("reactor.netty.http.server.HttpServer", classLoader)).isTrue();
    assertThat(ClassUtils.isPresent("org.springdoc.webflux.api.OpenApiResource", classLoader))
        .isTrue();
    assertThat(ClassUtils.isPresent("jakarta.servlet.Servlet", classLoader)).isFalse();
    assertThat(ClassUtils.isPresent("org.apache.catalina.startup.Tomcat", classLoader)).isFalse();
    assertThat(
            ClassUtils.isPresent("org.springframework.web.servlet.DispatcherServlet", classLoader))
        .isFalse();
    assertThat(ClassUtils.isPresent("org.springdoc.webmvc.api.OpenApiResource", classLoader))
        .isFalse();
    assertThat(ClassUtils.isPresent("jakarta.persistence.Entity", classLoader)).isFalse();
    assertThat(ClassUtils.isPresent("org.hibernate.SessionFactory", classLoader)).isFalse();
  }

  @Test
  @DisplayName("Should return a successful response through the default mapper")
  void shouldReturnSuccessfulResponse() {
    var body =
        getJson("/api/smoke/items/item_1", ClaimsHeaderTestBuilder.defaultUser(), HttpStatus.OK);

    assertThat(body.get("id").asString()).isEqualTo("item_1");
    assertThat(body.get("name").asString()).isEqualTo("First item");
    // Boot's application mapper still writes nulls, so the error-model omission below is not
    // caused by global non_null configuration.
    assertThat(body.has("description")).isTrue();
    assertThat(body.get("description").isNull()).isTrue();
  }

  @Test
  @DisplayName("Should return the standard unauthorized ApiErrorResponse without claims")
  void shouldReturnUnauthorizedWithoutClaims() {
    var body = getJson("/api/smoke/items/item_1", null, HttpStatus.UNAUTHORIZED);

    assertThat(body.get("type").asString()).isEqualTo("UNAUTHORIZED");
    assertThat(body.get("message").asString()).isNotBlank();
    assertThat(propertyNames(body)).containsExactly("type", "message");
  }

  @Test
  @DisplayName("Should enforce reactive @PreAuthorize with a forbidden ApiErrorResponse")
  void shouldReturnForbiddenWhenPermissionIsMissing() {
    var body =
        postJson(
            "/api/smoke/items",
            "{\"name\":\"New item\"}",
            ClaimsHeaderTestBuilder.user("usr_smoke_reader").withPermissions("smoke:read"),
            HttpStatus.FORBIDDEN);

    assertThat(body.get("type").asString()).isEqualTo("FORBIDDEN");
    assertThat(body.get("message").asString()).isNotBlank();
    assertThat(propertyNames(body)).containsExactly("type", "message");
  }

  @Test
  @DisplayName("Should allow a reactive method-secured request when permission is present")
  void shouldAllowMethodSecuredRequestWithPermission() {
    var body =
        postJson(
            "/api/smoke/items",
            "{\"name\":\"New item\"}",
            ClaimsHeaderTestBuilder.user("usr_smoke_writer").withPermissions("smoke:write"),
            HttpStatus.CREATED);

    assertThat(body.get("name").asString()).isEqualTo("New item");
  }

  @Test
  @DisplayName("Should omit absent optional fields from a not-found ApiErrorResponse")
  void shouldOmitAbsentOptionalFieldsFromNotFoundResponse() {
    var body =
        getJson(
            "/api/smoke/items/item_missing",
            ClaimsHeaderTestBuilder.defaultUser(),
            HttpStatus.NOT_FOUND);

    assertThat(body.get("type").asString()).isEqualTo("NOT_FOUND");
    assertThat(body.get("message").asString()).isEqualTo("Smoke item not found: item_missing");
    assertThat(propertyNames(body)).containsExactly("type", "message");
  }

  @Test
  @DisplayName("Should include the error code in a business ApiErrorResponse")
  void shouldIncludeErrorCodeInBusinessResponse() {
    var body =
        getJson(
            "/api/smoke/business-error",
            ClaimsHeaderTestBuilder.defaultUser(),
            HttpStatus.UNPROCESSABLE_CONTENT);

    assertThat(body.get("type").asString()).isEqualTo("APPLICATION_ERROR");
    assertThat(body.get("message").asString()).isEqualTo("Smoke business rule violated");
    assertThat(body.get("code").asString()).isEqualTo("SMOKE_RULE");
    assertThat(propertyNames(body)).containsExactly("type", "message", "code");
  }

  @Test
  @DisplayName("Should return validation field errors without absent optional field properties")
  void shouldReturnValidationFieldErrors() {
    var body =
        postJson(
            "/api/smoke/items",
            "{}",
            ClaimsHeaderTestBuilder.user("usr_smoke_writer").withPermissions("smoke:write"),
            HttpStatus.BAD_REQUEST);

    assertThat(body.get("type").asString()).isEqualTo("VALIDATION_ERROR");
    assertThat(propertyNames(body)).containsExactly("type", "message", "fieldErrors");
    var fieldError = body.get("fieldErrors").get(0);
    assertThat(fieldError.get("field").asString()).isEqualTo("name");
    assertThat(fieldError.get("message").asString()).isEqualTo("Name is required");
    assertThat(propertyNames(fieldError)).containsExactly("field", "message");
  }

  @Test
  @DisplayName("Should expose liveness and readiness probes without claims")
  void shouldExposeHealthProbesWithoutClaims() {
    for (var probePath : List.of("/actuator/health/liveness", "/actuator/health/readiness")) {
      webTestClient
          .get()
          .uri(probePath)
          .exchange()
          .expectStatus()
          .isOk()
          .expectBody()
          .jsonPath("$.status")
          .isEqualTo("UP");
    }
  }

  @Test
  @DisplayName("Should expose Prometheus metrics with the application tag without claims")
  void shouldExposePrometheusMetricsWithApplicationTag() {
    var body =
        webTestClient
            .get()
            .uri("/actuator/prometheus")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();

    assertThat(body).contains("jvm_memory_used_bytes{").contains("application=\"reactive-smoke\"");
  }

  @Test
  @DisplayName("Should generate OpenAPI documentation with shared error responses")
  void shouldGenerateOpenApiDocumentationWithSharedErrorResponses() {
    var apiDocs = getJson("/v3/api-docs", null, HttpStatus.OK);

    assertThat(apiDocs.get("info").get("title").asString()).isEqualTo("Reactive Smoke API");

    var getItemResponses = apiDocs.at("/paths/~1api~1smoke~1items~1{id}/get/responses");
    assertThat(propertyNames(getItemResponses))
        .containsExactlyInAnyOrder("200", "404", "500", "503");
    assertThat(getItemResponses.get("404").get("description").asString())
        .isEqualTo(SmokeItemController.EXPLICIT_NOT_FOUND_DESCRIPTION);
    assertStandardErrorResponse(getItemResponses.get("500"), "INTERNAL_ERROR");
    assertStandardErrorResponse(getItemResponses.get("503"), "SERVICE_UNAVAILABLE");

    var createItemResponses = apiDocs.at("/paths/~1api~1smoke~1items/post/responses");
    assertThat(propertyNames(createItemResponses))
        .containsExactlyInAnyOrder("201", "400", "401", "403", "500", "503");
    assertStandardErrorResponse(createItemResponses.get("400"), "INVALID_REQUEST");
    assertStandardErrorResponse(createItemResponses.get("401"), "UNAUTHORIZED");
    assertStandardErrorResponse(createItemResponses.get("403"), "FORBIDDEN");

    var businessErrorResponses = apiDocs.at("/paths/~1api~1smoke~1business-error/get/responses");
    assertThat(propertyNames(businessErrorResponses)).doesNotContain("401", "403", "404");

    var schemas = apiDocs.at("/components/schemas");
    assertThat(propertyNames(schemas.get("ApiErrorResponse").get("properties")))
        .contains("type", "message", "code", "fieldErrors");
    assertThat(propertyNames(schemas.get("FieldError").get("properties")))
        .contains("index", "field", "message", "rejectedValue");
  }

  private static void assertStandardErrorResponse(JsonNode response, String expectedType) {
    var mediaType = response.at("/content/application~1json");
    assertThat(mediaType.at("/schema/$ref").asString())
        .isEqualTo("#/components/schemas/ApiErrorResponse");
    assertThat(mediaType.at("/example/type").asString()).isEqualTo(expectedType);
    assertThat(mediaType.get("example").has("code")).isFalse();
  }

  private JsonNode getJson(
      String path, ClaimsHeaderTestBuilder claimsHeaderTestBuilder, HttpStatus expectedStatus) {
    var body =
        webTestClient
            .get()
            .uri(path)
            .headers(headers -> claimsHeaders(claimsHeaderTestBuilder).forEach(headers::add))
            .exchange()
            .expectStatus()
            .isEqualTo(expectedStatus.value())
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();
    return JSON_MAPPER.readTree(body);
  }

  private JsonNode postJson(
      String path,
      String requestBody,
      ClaimsHeaderTestBuilder claimsHeaderTestBuilder,
      HttpStatus expectedStatus) {
    var body =
        webTestClient
            .post()
            .uri(path)
            .contentType(MediaType.APPLICATION_JSON)
            .headers(headers -> claimsHeaders(claimsHeaderTestBuilder).forEach(headers::add))
            .bodyValue(requestBody)
            .exchange()
            .expectStatus()
            .isEqualTo(expectedStatus.value())
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();
    return JSON_MAPPER.readTree(body);
  }

  private static Map<String, String> claimsHeaders(
      ClaimsHeaderTestBuilder claimsHeaderTestBuilder) {
    return claimsHeaderTestBuilder == null ? Map.of() : claimsHeaderTestBuilder.buildHeaders();
  }

  private static List<String> propertyNames(JsonNode jsonNode) {
    return new ArrayList<>(jsonNode.propertyNames());
  }
}

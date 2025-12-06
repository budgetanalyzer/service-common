package org.budgetanalyzer.service.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

import org.budgetanalyzer.service.permission.dto.AuthorizeRequest;
import org.budgetanalyzer.service.permission.dto.AuthorizeResponse;
import org.budgetanalyzer.service.permission.dto.BulkAuthorizeRequest;
import org.budgetanalyzer.service.permission.dto.BulkAuthorizeResponse;

/** Unit tests for HttpPermissionClient. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HttpPermissionClientTest {

  private static final String USER_ID = "auth0|123";
  private static final String ACTION = "read";
  private static final String RESOURCE_TYPE = "transaction";
  private static final String RESOURCE_ID = "txn-456";
  private static final String OWNER_ID = "auth0|123";

  @Mock private StringRedisTemplate redisTemplate;

  @Mock private ValueOperations<String, String> valueOperations;

  private RestClient restClient;
  private RestClient.RequestBodyUriSpec requestBodyUriSpec;
  private RestClient.RequestBodySpec requestBodySpec;
  private RestClient.ResponseSpec responseSpec;

  private CircuitBreaker circuitBreaker;
  private PermissionClientProperties properties;
  private HttpPermissionClient client;

  @BeforeEach
  void setUp() {
    // Setup RestClient mock chain
    restClient = mock(RestClient.class);
    requestBodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
    requestBodySpec = mock(RestClient.RequestBodySpec.class);
    responseSpec = mock(RestClient.ResponseSpec.class);

    // Create real circuit breaker with permissive config for testing
    var config =
        CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .slidingWindowSize(10)
            .permittedNumberOfCallsInHalfOpenState(3)
            .waitDurationInOpenState(Duration.ofSeconds(1))
            .build();
    circuitBreaker = CircuitBreakerRegistry.of(config).circuitBreaker("test");

    // Setup properties
    properties = new PermissionClientProperties();
    properties.setBaseUrl("http://permission-service:8080");
    properties.getCache().setEnabled(true);
    properties.getCache().setTtl(Duration.ofMinutes(5));
    properties.getCache().setKeyPrefix("permissions");

    // Create client with mocks
    client = new HttpPermissionClient(restClient, redisTemplate, circuitBreaker, properties);
  }

  @Nested
  @DisplayName("canPerform (type-level authorization)")
  class CanPerformTests {

    @Test
    @DisplayName("should return cached result on cache hit")
    void shouldReturnCachedResultOnCacheHit() {
      // Given
      when(redisTemplate.opsForValue()).thenReturn(valueOperations);
      when(valueOperations.get("permissions:auth0|123:read:transaction")).thenReturn("1");

      // When
      var result = client.canPerform(AuthorizationContext.of(USER_ID), ACTION, RESOURCE_TYPE);

      // Then
      assertThat(result).isTrue();
      verify(restClient, never()).post(); // No HTTP call made
    }

    @Test
    @DisplayName("should call service on cache miss and cache result")
    void shouldCallServiceOnCacheMissAndCacheResult() {
      // Given
      setupRestClientMock(new AuthorizeResponse(true, "User has permission"));
      when(redisTemplate.opsForValue()).thenReturn(valueOperations);
      when(valueOperations.get(anyString())).thenReturn(null); // Cache miss

      // When
      var result = client.canPerform(AuthorizationContext.of(USER_ID), ACTION, RESOURCE_TYPE);

      // Then
      assertThat(result).isTrue();
      verify(valueOperations)
          .set(
              eq("permissions:auth0|123:read:transaction"),
              eq("1"),
              anyLong(),
              any(TimeUnit.class));
    }

    @Test
    @DisplayName("should return false when service denies access")
    void shouldReturnFalseWhenServiceDeniesAccess() {
      // Given
      setupRestClientMock(new AuthorizeResponse(false, "User lacks permission"));
      when(redisTemplate.opsForValue()).thenReturn(valueOperations);
      when(valueOperations.get(anyString())).thenReturn(null);

      // When
      var result = client.canPerform(AuthorizationContext.of(USER_ID), ACTION, RESOURCE_TYPE);

      // Then
      assertThat(result).isFalse();
      verify(valueOperations)
          .set(
              eq("permissions:auth0|123:read:transaction"),
              eq("0"),
              anyLong(),
              any(TimeUnit.class));
    }

    @Test
    @DisplayName("should fail closed when service throws exception")
    void shouldFailClosedWhenServiceThrowsException() {
      // Given
      when(redisTemplate.opsForValue()).thenReturn(valueOperations);
      when(valueOperations.get(anyString())).thenReturn(null);
      when(restClient.post()).thenThrow(new RestClientException("Connection refused"));

      // When
      var result = client.canPerform(AuthorizationContext.of(USER_ID), ACTION, RESOURCE_TYPE);

      // Then
      assertThat(result).isFalse(); // Fail closed
    }
  }

  @Nested
  @DisplayName("canAccess (resource-level authorization)")
  class CanAccessTests {

    @Test
    @DisplayName("should include resourceId in cache key")
    void shouldIncludeResourceIdInCacheKey() {
      // Given
      when(redisTemplate.opsForValue()).thenReturn(valueOperations);
      when(valueOperations.get("permissions:auth0|123:read:transaction:txn-456")).thenReturn("1");

      // When
      var result =
          client.canAccess(
              AuthorizationContext.of(USER_ID), ACTION, RESOURCE_TYPE, RESOURCE_ID, OWNER_ID);

      // Then
      assertThat(result).isTrue();
      verify(restClient, never()).post();
    }

    @Test
    @DisplayName("should call service with resourceId and ownerId on cache miss")
    void shouldCallServiceWithResourceIdAndOwnerIdOnCacheMiss() {
      // Given
      setupRestClientMock(new AuthorizeResponse(true, "Owner can access"));
      when(redisTemplate.opsForValue()).thenReturn(valueOperations);
      when(valueOperations.get(anyString())).thenReturn(null);

      // When
      var result =
          client.canAccess(
              AuthorizationContext.of(USER_ID), ACTION, RESOURCE_TYPE, RESOURCE_ID, OWNER_ID);

      // Then
      assertThat(result).isTrue();
      verify(restClient).post();
    }
  }

  @Nested
  @DisplayName("filterAccessible (bulk authorization)")
  class FilterAccessibleTests {

    @Test
    @DisplayName("should return accessible resources from bulk endpoint")
    void shouldReturnAccessibleResourcesFromBulkEndpoint() {
      // Given
      setupBulkRestClientMock(new BulkAuthorizeResponse(Set.of("txn-1", "txn-3")));

      var resourceIdToOwnerId =
          Map.of(
              "txn-1", "auth0|123",
              "txn-2", "auth0|456",
              "txn-3", "auth0|123");

      // When
      var result =
          client.filterAccessible(
              AuthorizationContext.of(USER_ID), ACTION, RESOURCE_TYPE, resourceIdToOwnerId);

      // Then
      assertThat(result).containsExactlyInAnyOrder("txn-1", "txn-3");
    }

    @Test
    @DisplayName("should return empty set for empty input")
    void shouldReturnEmptySetForEmptyInput() {
      // When
      var result =
          client.filterAccessible(
              AuthorizationContext.of(USER_ID), ACTION, RESOURCE_TYPE, Map.of());

      // Then
      assertThat(result).isEmpty();
      verify(restClient, never()).post();
    }

    @Test
    @DisplayName("should return empty set when service throws exception")
    void shouldReturnEmptySetWhenServiceThrowsException() {
      // Given
      when(restClient.post()).thenThrow(new RestClientException("Connection refused"));

      // When
      var result =
          client.filterAccessible(
              AuthorizationContext.of(USER_ID),
              ACTION,
              RESOURCE_TYPE,
              Map.of("txn-1", "auth0|123"));

      // Then
      assertThat(result).isEmpty(); // Fail closed - deny all
    }
  }

  @Nested
  @DisplayName("Caching behavior")
  class CachingTests {

    @Test
    @DisplayName("should skip cache when caching is disabled")
    void shouldSkipCacheWhenCachingIsDisabled() {
      // Given
      properties.getCache().setEnabled(false);
      client = new HttpPermissionClient(restClient, redisTemplate, circuitBreaker, properties);
      setupRestClientMock(new AuthorizeResponse(true, "Allowed"));

      // When
      var result = client.canPerform(AuthorizationContext.of(USER_ID), ACTION, RESOURCE_TYPE);

      // Then
      assertThat(result).isTrue();
      verify(redisTemplate, never()).opsForValue();
    }

    @Test
    @DisplayName("should skip cache when redis template is null")
    void shouldSkipCacheWhenRedisTemplateIsNull() {
      // Given
      client = new HttpPermissionClient(restClient, null, circuitBreaker, properties);
      setupRestClientMock(new AuthorizeResponse(true, "Allowed"));

      // When
      var result = client.canPerform(AuthorizationContext.of(USER_ID), ACTION, RESOURCE_TYPE);

      // Then
      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("should proceed to service when cache lookup fails")
    void shouldProceedToServiceWhenCacheLookupFails() {
      // Given
      setupRestClientMock(new AuthorizeResponse(true, "Allowed"));
      when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis connection failed"));

      // When
      var result = client.canPerform(AuthorizationContext.of(USER_ID), ACTION, RESOURCE_TYPE);

      // Then
      assertThat(result).isTrue();
      verify(restClient).post();
    }
  }

  private void setupRestClientMock(AuthorizeResponse response) {
    when(restClient.post()).thenReturn(requestBodyUriSpec);
    when(requestBodyUriSpec.uri("/v1/authorize")).thenReturn(requestBodySpec);
    when(requestBodySpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(requestBodySpec);
    when(requestBodySpec.body(any(AuthorizeRequest.class))).thenReturn(requestBodySpec);
    when(requestBodySpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.body(AuthorizeResponse.class)).thenReturn(response);
  }

  private void setupBulkRestClientMock(BulkAuthorizeResponse response) {
    when(restClient.post()).thenReturn(requestBodyUriSpec);
    when(requestBodyUriSpec.uri("/v1/authorize/bulk")).thenReturn(requestBodySpec);
    when(requestBodySpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(requestBodySpec);
    when(requestBodySpec.body(any(BulkAuthorizeRequest.class))).thenReturn(requestBodySpec);
    when(requestBodySpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.body(BulkAuthorizeResponse.class)).thenReturn(response);
  }
}

package org.budgetanalyzer.service.permission;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;

import org.budgetanalyzer.service.permission.dto.AuthorizeRequest;
import org.budgetanalyzer.service.permission.dto.AuthorizeRequestContext;
import org.budgetanalyzer.service.permission.dto.AuthorizeResponse;
import org.budgetanalyzer.service.permission.dto.BulkAuthorizeRequest;
import org.budgetanalyzer.service.permission.dto.BulkAuthorizeResponse;

/**
 * HTTP implementation of PermissionClient with Redis caching and circuit breaker.
 *
 * <p>This implementation:
 *
 * <ul>
 *   <li>Calls permission-service via HTTP REST API
 *   <li>Caches authorization results in Redis with configurable TTL
 *   <li>Uses Resilience4j circuit breaker to handle service failures
 *   <li>Fails closed when permission-service is unavailable and cache is empty
 * </ul>
 *
 * <p>Cache key pattern: {@code permissions:{userId}:{action}:{resourceType}[:resourceId]}
 *
 * @see PermissionClient
 * @see PermissionClientProperties
 */
public class HttpPermissionClient implements PermissionClient {

  private static final Logger logger = LoggerFactory.getLogger(HttpPermissionClient.class);

  private static final String GRANTED_VALUE = "1";
  private static final String DENIED_VALUE = "0";

  private final RestClient restClient;
  private final StringRedisTemplate redisTemplate;
  private final CircuitBreaker circuitBreaker;
  private final PermissionClientProperties properties;

  /**
   * Constructs an HttpPermissionClient.
   *
   * @param restClient the REST client configured for permission-service
   * @param redisTemplate the Redis template for caching (may be null if caching disabled)
   * @param circuitBreaker the circuit breaker for fault tolerance
   * @param properties the configuration properties
   */
  public HttpPermissionClient(
      RestClient restClient,
      StringRedisTemplate redisTemplate,
      CircuitBreaker circuitBreaker,
      PermissionClientProperties properties) {
    this.restClient = restClient;
    this.redisTemplate = redisTemplate;
    this.circuitBreaker = circuitBreaker;
    this.properties = properties;
  }

  @Override
  public boolean canPerform(AuthorizationContext ctx, String action, String resourceType) {
    var cacheKey = buildCacheKey(ctx.userId(), action, resourceType, null);
    return checkWithCache(cacheKey, () -> doCanPerform(ctx, action, resourceType));
  }

  @Override
  public boolean canAccess(
      AuthorizationContext ctx,
      String action,
      String resourceType,
      String resourceId,
      String ownerId) {
    var cacheKey = buildCacheKey(ctx.userId(), action, resourceType, resourceId);
    return checkWithCache(
        cacheKey, () -> doCanAccess(ctx, action, resourceType, resourceId, ownerId));
  }

  @Override
  public Set<String> filterAccessible(
      AuthorizationContext ctx,
      String action,
      String resourceType,
      Map<String, String> resourceIdToOwnerId) {
    if (resourceIdToOwnerId == null || resourceIdToOwnerId.isEmpty()) {
      return Collections.emptySet();
    }

    // Bulk operations are not cached individually - go directly to service
    return circuitBreaker.executeSupplier(
        () -> {
          try {
            return doFilterAccessible(ctx, action, resourceType, resourceIdToOwnerId);
          } catch (RestClientException e) {
            logger.error(
                "Failed to filter accessible resources: action={}, resourceType={}, count={}",
                action,
                resourceType,
                resourceIdToOwnerId.size(),
                e);
            // Fail closed - deny access to all resources
            return Collections.emptySet();
          }
        });
  }

  /**
   * Checks authorization with cache lookup, falling back to service call on cache miss.
   *
   * @param cacheKey the cache key
   * @param serviceCall the supplier that makes the actual service call
   * @return true if authorized, false otherwise
   */
  private boolean checkWithCache(
      String cacheKey, java.util.function.Supplier<Boolean> serviceCall) {
    // Check cache first
    if (isCacheEnabled()) {
      try {
        var cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
          logger.debug("Cache hit for key: {}", cacheKey);
          return GRANTED_VALUE.equals(cached);
        }
        logger.debug("Cache miss for key: {}", cacheKey);
      } catch (Exception e) {
        logger.warn("Redis cache lookup failed for key: {}, proceeding to service", cacheKey, e);
      }
    }

    // Call service with circuit breaker
    return circuitBreaker.executeSupplier(
        () -> {
          try {
            var result = serviceCall.get();
            // Cache the result
            if (isCacheEnabled()) {
              cacheResult(cacheKey, result);
            }
            return result;
          } catch (RestClientException e) {
            logger.error("Permission service call failed for key: {}", cacheKey, e);
            // Fail closed - deny access
            return false;
          }
        });
  }

  /**
   * Makes the actual HTTP call for type-level authorization.
   *
   * @param ctx the authorization context
   * @param action the action
   * @param resourceType the resource type
   * @return true if authorized
   */
  private boolean doCanPerform(AuthorizationContext ctx, String action, String resourceType) {
    var request =
        AuthorizeRequest.forType(
            ctx.userId(), action, resourceType, AuthorizeRequestContext.from(ctx));

    var response =
        restClient
            .post()
            .uri("/v1/authorize")
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .body(AuthorizeResponse.class);

    if (response == null) {
      logger.warn(
          "Null response from permission-service: userId={}, action={}, resourceType={}",
          ctx.userId(),
          action,
          resourceType);
      return false;
    }

    logger.debug(
        "Authorization result: userId={}, action={}, resourceType={}, granted={}, reason={}",
        ctx.userId(),
        action,
        resourceType,
        response.granted(),
        response.reason());

    return response.granted();
  }

  /**
   * Makes the actual HTTP call for resource-level authorization.
   *
   * @param ctx the authorization context
   * @param action the action
   * @param resourceType the resource type
   * @param resourceId the resource ID
   * @param ownerId the owner ID
   * @return true if authorized
   */
  private boolean doCanAccess(
      AuthorizationContext ctx,
      String action,
      String resourceType,
      String resourceId,
      String ownerId) {
    var request =
        AuthorizeRequest.forResource(
            ctx.userId(),
            action,
            resourceType,
            resourceId,
            ownerId,
            AuthorizeRequestContext.from(ctx));

    var response =
        restClient
            .post()
            .uri("/v1/authorize")
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .body(AuthorizeResponse.class);

    if (response == null) {
      logger.warn(
          "Null response from permission-service: userId={}, action={}, resourceType={}, "
              + "resourceId={}",
          ctx.userId(),
          action,
          resourceType,
          resourceId);
      return false;
    }

    logger.debug(
        "Authorization result: userId={}, action={}, resourceType={}, resourceId={}, "
            + "granted={}, reason={}",
        ctx.userId(),
        action,
        resourceType,
        resourceId,
        response.granted(),
        response.reason());

    return response.granted();
  }

  /**
   * Makes the actual HTTP call for bulk authorization.
   *
   * @param ctx the authorization context
   * @param action the action
   * @param resourceType the resource type
   * @param resourceIdToOwnerId the resource ID to owner ID mapping
   * @return set of accessible resource IDs
   */
  private Set<String> doFilterAccessible(
      AuthorizationContext ctx,
      String action,
      String resourceType,
      Map<String, String> resourceIdToOwnerId) {
    var request = new BulkAuthorizeRequest(ctx.userId(), action, resourceType, resourceIdToOwnerId);

    var response =
        restClient
            .post()
            .uri("/v1/authorize/bulk")
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .body(BulkAuthorizeResponse.class);

    if (response == null || response.accessible() == null) {
      logger.warn(
          "Null response from permission-service bulk endpoint: userId={}, action={}, "
              + "resourceType={}",
          ctx.userId(),
          action,
          resourceType);
      return Collections.emptySet();
    }

    logger.debug(
        "Bulk authorization result: userId={}, action={}, resourceType={}, "
            + "requested={}, accessible={}",
        ctx.userId(),
        action,
        resourceType,
        resourceIdToOwnerId.size(),
        response.accessible().size());

    return response.accessible();
  }

  /**
   * Builds a Redis cache key for the given authorization parameters.
   *
   * @param userId the user ID
   * @param action the action
   * @param resourceType the resource type
   * @param resourceId the resource ID (may be null for type-level checks)
   * @return the cache key
   */
  private String buildCacheKey(
      String userId, String action, String resourceType, String resourceId) {
    var prefix = properties.getCache().getKeyPrefix();
    if (resourceId != null) {
      return String.format("%s:%s:%s:%s:%s", prefix, userId, action, resourceType, resourceId);
    }
    return String.format("%s:%s:%s:%s", prefix, userId, action, resourceType);
  }

  /**
   * Caches an authorization result in Redis.
   *
   * @param cacheKey the cache key
   * @param granted whether access was granted
   */
  private void cacheResult(String cacheKey, boolean granted) {
    try {
      var ttl = properties.getCache().getTtl();
      var value = granted ? GRANTED_VALUE : DENIED_VALUE;
      redisTemplate.opsForValue().set(cacheKey, value, ttl.toMillis(), TimeUnit.MILLISECONDS);
      logger.debug(
          "Cached authorization result: key={}, granted={}, ttl={}", cacheKey, granted, ttl);
    } catch (Exception e) {
      logger.warn("Failed to cache authorization result for key: {}", cacheKey, e);
    }
  }

  /**
   * Checks if Redis caching is enabled and available.
   *
   * @return true if caching is enabled and Redis template is available
   */
  private boolean isCacheEnabled() {
    return properties.getCache().isEnabled() && redisTemplate != null;
  }
}

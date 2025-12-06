package org.budgetanalyzer.service.permission;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the PermissionClient.
 *
 * <p>Usage in application.yml:
 *
 * <pre>
 * budgetanalyzer:
 *   permission:
 *     enabled: true
 *     base-url: http://permission-service:8080
 *     cache:
 *       enabled: true
 *       ttl: 5m
 *     circuit-breaker:
 *       failure-rate-threshold: 50
 *       wait-duration-in-open-state: 60s
 *       permitted-calls-in-half-open-state: 3
 *       sliding-window-size: 10
 *     connect-timeout: 5s
 *     read-timeout: 10s
 * </pre>
 */
@ConfigurationProperties(prefix = "budgetanalyzer.permission")
public class PermissionClientProperties {

  /** Enable/disable the PermissionClient. When disabled, all permission checks return false. */
  private boolean enabled = true;

  /** Base URL of the permission-service. */
  private String baseUrl = "http://permission-service:8080";

  /** Connection timeout for HTTP requests. */
  private Duration connectTimeout = Duration.ofSeconds(5);

  /** Read timeout for HTTP requests. */
  private Duration readTimeout = Duration.ofSeconds(10);

  /** Cache configuration. */
  private CacheProperties cache = new CacheProperties();

  /** Circuit breaker configuration. */
  private CircuitBreakerProperties circuitBreaker = new CircuitBreakerProperties();

  /**
   * Checks whether the permission client is enabled.
   *
   * @return true if enabled, false otherwise
   */
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Sets whether the permission client is enabled.
   *
   * @param enabled true to enable, false to disable
   */
  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  /**
   * Gets the base URL of the permission-service.
   *
   * @return the base URL
   */
  public String getBaseUrl() {
    return baseUrl;
  }

  /**
   * Sets the base URL of the permission-service.
   *
   * @param baseUrl the base URL
   */
  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  /**
   * Gets the connection timeout.
   *
   * @return the connection timeout
   */
  public Duration getConnectTimeout() {
    return connectTimeout;
  }

  /**
   * Sets the connection timeout.
   *
   * @param connectTimeout the connection timeout
   */
  public void setConnectTimeout(Duration connectTimeout) {
    this.connectTimeout = connectTimeout;
  }

  /**
   * Gets the read timeout.
   *
   * @return the read timeout
   */
  public Duration getReadTimeout() {
    return readTimeout;
  }

  /**
   * Sets the read timeout.
   *
   * @param readTimeout the read timeout
   */
  public void setReadTimeout(Duration readTimeout) {
    this.readTimeout = readTimeout;
  }

  /**
   * Gets the cache configuration.
   *
   * @return the cache properties
   */
  public CacheProperties getCache() {
    return cache;
  }

  /**
   * Sets the cache configuration.
   *
   * @param cache the cache properties
   */
  public void setCache(CacheProperties cache) {
    this.cache = cache;
  }

  /**
   * Gets the circuit breaker configuration.
   *
   * @return the circuit breaker properties
   */
  public CircuitBreakerProperties getCircuitBreaker() {
    return circuitBreaker;
  }

  /**
   * Sets the circuit breaker configuration.
   *
   * @param circuitBreaker the circuit breaker properties
   */
  public void setCircuitBreaker(CircuitBreakerProperties circuitBreaker) {
    this.circuitBreaker = circuitBreaker;
  }

  /** Cache configuration properties. */
  public static class CacheProperties {

    /** Enable/disable Redis caching. */
    private boolean enabled = true;

    /** Cache TTL (time-to-live). */
    private Duration ttl = Duration.ofMinutes(5);

    /** Redis key prefix for permission cache entries. */
    private String keyPrefix = "permissions";

    /** Redis channel name for cache invalidation events. */
    private String invalidationChannel = "permission-invalidation";

    /**
     * Checks whether caching is enabled.
     *
     * @return true if enabled, false otherwise
     */
    public boolean isEnabled() {
      return enabled;
    }

    /**
     * Sets whether caching is enabled.
     *
     * @param enabled true to enable, false to disable
     */
    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    /**
     * Gets the cache TTL.
     *
     * @return the TTL duration
     */
    public Duration getTtl() {
      return ttl;
    }

    /**
     * Sets the cache TTL.
     *
     * @param ttl the TTL duration
     */
    public void setTtl(Duration ttl) {
      this.ttl = ttl;
    }

    /**
     * Gets the Redis key prefix.
     *
     * @return the key prefix
     */
    public String getKeyPrefix() {
      return keyPrefix;
    }

    /**
     * Sets the Redis key prefix.
     *
     * @param keyPrefix the key prefix
     */
    public void setKeyPrefix(String keyPrefix) {
      this.keyPrefix = keyPrefix;
    }

    /**
     * Gets the Redis invalidation channel name.
     *
     * @return the channel name
     */
    public String getInvalidationChannel() {
      return invalidationChannel;
    }

    /**
     * Sets the Redis invalidation channel name.
     *
     * @param invalidationChannel the channel name
     */
    public void setInvalidationChannel(String invalidationChannel) {
      this.invalidationChannel = invalidationChannel;
    }
  }

  /** Circuit breaker configuration properties. */
  public static class CircuitBreakerProperties {

    /** Failure rate threshold (percentage) to open the circuit. */
    private int failureRateThreshold = 50;

    /** Duration to wait in open state before transitioning to half-open. */
    private Duration waitDurationInOpenState = Duration.ofSeconds(60);

    /** Number of calls permitted in half-open state. */
    private int permittedCallsInHalfOpenState = 3;

    /** Size of the sliding window for calculating failure rate. */
    private int slidingWindowSize = 10;

    /**
     * Gets the failure rate threshold.
     *
     * @return the failure rate threshold percentage
     */
    public int getFailureRateThreshold() {
      return failureRateThreshold;
    }

    /**
     * Sets the failure rate threshold.
     *
     * @param failureRateThreshold the failure rate threshold percentage
     */
    public void setFailureRateThreshold(int failureRateThreshold) {
      this.failureRateThreshold = failureRateThreshold;
    }

    /**
     * Gets the wait duration in open state.
     *
     * @return the wait duration
     */
    public Duration getWaitDurationInOpenState() {
      return waitDurationInOpenState;
    }

    /**
     * Sets the wait duration in open state.
     *
     * @param waitDurationInOpenState the wait duration
     */
    public void setWaitDurationInOpenState(Duration waitDurationInOpenState) {
      this.waitDurationInOpenState = waitDurationInOpenState;
    }

    /**
     * Gets the number of permitted calls in half-open state.
     *
     * @return the number of permitted calls
     */
    public int getPermittedCallsInHalfOpenState() {
      return permittedCallsInHalfOpenState;
    }

    /**
     * Sets the number of permitted calls in half-open state.
     *
     * @param permittedCallsInHalfOpenState the number of permitted calls
     */
    public void setPermittedCallsInHalfOpenState(int permittedCallsInHalfOpenState) {
      this.permittedCallsInHalfOpenState = permittedCallsInHalfOpenState;
    }

    /**
     * Gets the sliding window size.
     *
     * @return the sliding window size
     */
    public int getSlidingWindowSize() {
      return slidingWindowSize;
    }

    /**
     * Sets the sliding window size.
     *
     * @param slidingWindowSize the sliding window size
     */
    public void setSlidingWindowSize(int slidingWindowSize) {
      this.slidingWindowSize = slidingWindowSize;
    }
  }
}

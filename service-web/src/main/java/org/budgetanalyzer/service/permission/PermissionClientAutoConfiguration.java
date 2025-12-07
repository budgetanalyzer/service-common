package org.budgetanalyzer.service.permission;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

/**
 * Auto-configuration for PermissionClient.
 *
 * <p>Configures:
 *
 * <ul>
 *   <li>HttpPermissionClient with RestClient for HTTP communication
 *   <li>Redis caching with configurable TTL (optional, requires Redis on classpath)
 *   <li>Redis pub/sub for cache invalidation (optional, requires Redis)
 *   <li>Resilience4j circuit breaker for fault tolerance
 * </ul>
 *
 * <p>This configuration is opt-in. Enable by setting {@code budgetanalyzer.permission.enabled=true}
 * in your application configuration.
 *
 * @see PermissionClient
 * @see PermissionClientProperties
 */
@AutoConfiguration
@EnableConfigurationProperties(PermissionClientProperties.class)
@ConditionalOnProperty(
    prefix = "budgetanalyzer.permission",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = false)
public class PermissionClientAutoConfiguration {

  private static final Logger logger =
      LoggerFactory.getLogger(PermissionClientAutoConfiguration.class);

  private static final String CIRCUIT_BREAKER_NAME = "permissionClient";

  /**
   * Creates the RestClient for communicating with permission-service.
   *
   * @param properties the configuration properties
   * @return the configured RestClient
   */
  @Bean
  @ConditionalOnMissingBean(name = "permissionRestClient")
  public RestClient permissionRestClient(PermissionClientProperties properties) {
    logger.info(
        "Configuring permission-service RestClient with baseUrl: {}", properties.getBaseUrl());

    var settings =
        ClientHttpRequestFactorySettings.defaults()
            .withConnectTimeout(properties.getConnectTimeout())
            .withReadTimeout(properties.getReadTimeout());

    var requestFactory = ClientHttpRequestFactoryBuilder.detect().build(settings);

    return RestClient.builder()
        .baseUrl(properties.getBaseUrl())
        .requestFactory(requestFactory)
        .build();
  }

  /**
   * Creates the circuit breaker for permission-service calls.
   *
   * @param properties the configuration properties
   * @return the configured CircuitBreaker
   */
  @Bean
  @ConditionalOnMissingBean(name = "permissionCircuitBreaker")
  public CircuitBreaker permissionCircuitBreaker(PermissionClientProperties properties) {
    var cbProps = properties.getCircuitBreaker();

    var config =
        CircuitBreakerConfig.custom()
            .failureRateThreshold(cbProps.getFailureRateThreshold())
            .waitDurationInOpenState(cbProps.getWaitDurationInOpenState())
            .permittedNumberOfCallsInHalfOpenState(cbProps.getPermittedCallsInHalfOpenState())
            .slidingWindowSize(cbProps.getSlidingWindowSize())
            .build();

    var registry = CircuitBreakerRegistry.of(config);
    var circuitBreaker = registry.circuitBreaker(CIRCUIT_BREAKER_NAME);

    logger.info(
        "Configured permission circuit breaker: failureRateThreshold={}, "
            + "waitDurationInOpenState={}, slidingWindowSize={}",
        cbProps.getFailureRateThreshold(),
        cbProps.getWaitDurationInOpenState(),
        cbProps.getSlidingWindowSize());

    return circuitBreaker;
  }

  /**
   * Creates the PermissionClient implementation.
   *
   * @param permissionRestClient the REST client for permission service
   * @param redisTemplate the Redis template (may be null if Redis unavailable)
   * @param permissionCircuitBreaker the circuit breaker for permission service calls
   * @param properties the configuration properties
   * @return the PermissionClient implementation
   */
  @Bean
  @ConditionalOnMissingBean(PermissionClient.class)
  public PermissionClient permissionClient(
      RestClient permissionRestClient,
      StringRedisTemplate redisTemplate,
      CircuitBreaker permissionCircuitBreaker,
      PermissionClientProperties properties) {

    logger.info(
        "Creating HttpPermissionClient: baseUrl={}, cacheEnabled={}, cacheTtl={}",
        properties.getBaseUrl(),
        properties.getCache().isEnabled(),
        properties.getCache().getTtl());

    return new HttpPermissionClient(
        permissionRestClient, redisTemplate, permissionCircuitBreaker, properties);
  }

  /**
   * Configuration for Redis-based caching and cache invalidation.
   *
   * <p>Only activated when Redis is on the classpath and caching is enabled. This is a nested
   * configuration that requires the parent auto-configuration to be active.
   */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnProperty(
      prefix = "budgetanalyzer.permission",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = false)
  @ConditionalOnClass(RedisConnectionFactory.class)
  static class RedisCacheConfiguration {

    private static final Logger redisLogger =
        LoggerFactory.getLogger(RedisCacheConfiguration.class);

    /**
     * Creates a StringRedisTemplate for permission caching if not already defined.
     *
     * @param connectionFactory the Redis connection factory
     * @return the StringRedisTemplate
     */
    @Bean
    @ConditionalOnMissingBean(StringRedisTemplate.class)
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
      return new StringRedisTemplate(connectionFactory);
    }

    /**
     * Creates the Redis message listener container for cache invalidation.
     *
     * @param connectionFactory the Redis connection factory
     * @param listener the cache invalidation listener
     * @param properties the configuration properties
     * @return the configured listener container
     */
    @Bean
    @ConditionalOnMissingBean(name = "permissionInvalidationListenerContainer")
    public RedisMessageListenerContainer permissionInvalidationListenerContainer(
        RedisConnectionFactory connectionFactory,
        PermissionCacheInvalidationListener permissionCacheInvalidationListener,
        PermissionClientProperties properties) {

      var channel = properties.getCache().getInvalidationChannel();
      redisLogger.info(
          "Setting up Redis pub/sub for permission invalidation on channel: {}", channel);

      var container = new RedisMessageListenerContainer();
      container.setConnectionFactory(connectionFactory);
      container.addMessageListener(permissionCacheInvalidationListener, new ChannelTopic(channel));

      return container;
    }

    /**
     * Creates the cache invalidation listener.
     *
     * @param redisTemplate the Redis template
     * @param objectMapper the JSON object mapper
     * @param properties the configuration properties
     * @return the cache invalidation listener
     */
    @Bean
    @ConditionalOnMissingBean(PermissionCacheInvalidationListener.class)
    public PermissionCacheInvalidationListener permissionCacheInvalidationListener(
        StringRedisTemplate redisTemplate,
        ObjectMapper objectMapper,
        PermissionClientProperties properties) {

      return new PermissionCacheInvalidationListener(
          redisTemplate, objectMapper, properties.getCache().getKeyPrefix());
    }
  }

  /**
   * Configuration when Redis is not available.
   *
   * <p>Creates a null-safe PermissionClient without caching. This is a nested configuration that
   * requires the parent auto-configuration to be active.
   */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnProperty(
      prefix = "budgetanalyzer.permission",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = false)
  @ConditionalOnMissingBean(StringRedisTemplate.class)
  static class NoCacheConfiguration {

    private static final Logger noCacheLogger = LoggerFactory.getLogger(NoCacheConfiguration.class);

    /**
     * Creates the PermissionClient without Redis caching.
     *
     * @param restClient the REST client
     * @param circuitBreaker the circuit breaker
     * @param properties the configuration properties
     * @return the PermissionClient implementation without caching
     */
    @Bean
    @ConditionalOnMissingBean(PermissionClient.class)
    public PermissionClient permissionClientNoCache(
        RestClient permissionRestClient,
        CircuitBreaker permissionCircuitBreaker,
        PermissionClientProperties properties) {

      noCacheLogger.warn(
          "Redis not available - PermissionClient will operate without caching. "
              + "This may impact performance.");

      // Disable cache in properties since Redis is not available
      properties.getCache().setEnabled(false);

      return new HttpPermissionClient(
          permissionRestClient, null, permissionCircuitBreaker, properties);
    }
  }
}

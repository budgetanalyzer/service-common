package org.budgetanalyzer.service.permission;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Redis pub/sub listener for permission cache invalidation events.
 *
 * <p>Listens to the {@code permission-invalidation} channel and clears cached permissions when user
 * permissions change. This ensures that permission revocations take effect immediately.
 *
 * <p>Expected message format:
 *
 * <pre>{@code
 * {
 *   "type": "USER_PERMISSIONS_CHANGED",
 *   "userId": "auth0|123",
 *   "timestamp": "2025-12-06T10:30:00Z"
 * }
 * }</pre>
 */
public class PermissionCacheInvalidationListener implements MessageListener {

  private static final Logger logger =
      LoggerFactory.getLogger(PermissionCacheInvalidationListener.class);

  private static final String TYPE_USER_PERMISSIONS_CHANGED = "USER_PERMISSIONS_CHANGED";

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;
  private final String keyPrefix;

  /**
   * Constructs a PermissionCacheInvalidationListener.
   *
   * @param redisTemplate the Redis template for cache operations
   * @param objectMapper the JSON object mapper for parsing messages
   * @param keyPrefix the Redis key prefix for permission cache entries
   */
  public PermissionCacheInvalidationListener(
      StringRedisTemplate redisTemplate, ObjectMapper objectMapper, String keyPrefix) {
    this.redisTemplate = redisTemplate;
    this.objectMapper = objectMapper;
    this.keyPrefix = keyPrefix;
  }

  @Override
  public void onMessage(Message message, byte[] pattern) {
    try {
      var body = new String(message.getBody());
      logger.debug("Received cache invalidation message: {}", body);

      var json = objectMapper.readTree(body);
      var type = json.path("type").asText();
      var userId = json.path("userId").asText();

      if (TYPE_USER_PERMISSIONS_CHANGED.equals(type) && !userId.isBlank()) {
        invalidateUserPermissions(userId);
      } else {
        logger.warn("Unrecognized cache invalidation message type: {} or missing userId", type);
      }
    } catch (Exception e) {
      logger.error("Failed to process cache invalidation message", e);
    }
  }

  /**
   * Invalidates all cached permissions for a specific user.
   *
   * @param userId the user ID whose permissions should be invalidated
   */
  private void invalidateUserPermissions(String userId) {
    try {
      // Build pattern to match all keys for this user: permissions:userId:*
      var keyPattern = String.format("%s:%s:*", keyPrefix, userId);

      // Find and delete all matching keys
      Set<String> keys = redisTemplate.keys(keyPattern);
      if (keys != null && !keys.isEmpty()) {
        redisTemplate.delete(keys);
        logger.info("Invalidated {} cached permission entries for user: {}", keys.size(), userId);
      } else {
        logger.debug("No cached permissions found for user: {}", userId);
      }
    } catch (Exception e) {
      logger.error("Failed to invalidate cached permissions for user: {}", userId, e);
    }
  }
}

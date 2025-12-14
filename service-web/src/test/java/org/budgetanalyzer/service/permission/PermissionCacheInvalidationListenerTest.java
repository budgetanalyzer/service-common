package org.budgetanalyzer.service.permission;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Unit tests for PermissionCacheInvalidationListener. */
@ExtendWith(MockitoExtension.class)
class PermissionCacheInvalidationListenerTest {

  private static final String KEY_PREFIX = "permissions";

  @Mock private StringRedisTemplate redisTemplate;

  private ObjectMapper objectMapper;
  private PermissionCacheInvalidationListener listener;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    listener = new PermissionCacheInvalidationListener(redisTemplate, objectMapper, KEY_PREFIX);
  }

  @Test
  @DisplayName("should invalidate user permissions on USER_PERMISSIONS_CHANGED message")
  void shouldInvalidateUserPermissionsOnUserPermissionsChangedMessage() {
    // Given
    var messageBody =
        """
        {
          "type": "USER_PERMISSIONS_CHANGED",
          "userId": "auth0|123",
          "timestamp": "2025-12-06T10:30:00Z"
        }
        """;
    var message = createMessage(messageBody);
    var matchingKeys =
        Set.of("permissions:auth0|123:read:transaction", "permissions:auth0|123:write:transaction");
    when(redisTemplate.keys("permissions:auth0|123:*")).thenReturn(matchingKeys);

    // When
    listener.onMessage(message, null);

    // Then
    verify(redisTemplate).keys("permissions:auth0|123:*");
    verify(redisTemplate).delete(matchingKeys);
  }

  @Test
  @DisplayName("should not delete when no matching keys found")
  void shouldNotDeleteWhenNoMatchingKeysFound() {
    // Given
    var messageBody =
        """
        {
          "type": "USER_PERMISSIONS_CHANGED",
          "userId": "auth0|123",
          "timestamp": "2025-12-06T10:30:00Z"
        }
        """;
    var message = createMessage(messageBody);
    when(redisTemplate.keys("permissions:auth0|123:*")).thenReturn(Set.of());

    // When
    listener.onMessage(message, null);

    // Then
    verify(redisTemplate).keys("permissions:auth0|123:*");
    verify(redisTemplate, never()).delete(anySet());
  }

  @Test
  @DisplayName("should ignore unknown message types")
  void shouldIgnoreUnknownMessageTypes() {
    // Given
    var messageBody =
        """
        {
          "type": "UNKNOWN_TYPE",
          "userId": "auth0|123"
        }
        """;
    var message = createMessage(messageBody);

    // When
    listener.onMessage(message, null);

    // Then
    verify(redisTemplate, never()).keys(any());
    verify(redisTemplate, never()).delete(anySet());
  }

  @Test
  @DisplayName("should ignore messages with missing userId")
  void shouldIgnoreMessagesWithMissingUserId() {
    // Given
    var messageBody =
        """
        {
          "type": "USER_PERMISSIONS_CHANGED"
        }
        """;
    var message = createMessage(messageBody);

    // When
    listener.onMessage(message, null);

    // Then
    verify(redisTemplate, never()).keys(any());
  }

  @Test
  @DisplayName("should handle malformed JSON gracefully")
  void shouldHandleMalformedJsonGracefully() {
    // Given
    var message = createMessage("not valid json");

    // When - should not throw
    listener.onMessage(message, null);

    // Then - no exception thrown and no Redis operations
    verify(redisTemplate, never()).keys(any());
  }

  private Message createMessage(String body) {
    var message = mock(Message.class);
    when(message.getBody()).thenReturn(body.getBytes());
    return message;
  }
}

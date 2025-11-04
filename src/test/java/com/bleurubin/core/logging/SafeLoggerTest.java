package com.bleurubin.core.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Comprehensive unit tests for SafeLogger.
 *
 * <p>Tests JSON serialization, string masking, edge cases, thread safety, and integration with
 * Jackson ObjectMapper.
 */
class SafeLoggerTest {

  // ==================== Test Data Classes ====================

  /** Simple POJO for basic serialization tests. */
  static class SimpleUser {
    private String name;
    private int age;

    SimpleUser(String name, int age) {
      this.name = name;
      this.age = age;
    }

    public String getName() {
      return name;
    }

    public int getAge() {
      return age;
    }
  }

  /** POJO with sensitive fields for masking tests. */
  static class SensitiveUser {
    private String username;
    @Sensitive private String password;

    @Sensitive(showLast = 4)
    private String creditCard;

    @Sensitive(maskChar = '#')
    private String ssn;

    SensitiveUser(String username, String password, String creditCard, String ssn) {
      this.username = username;
      this.password = password;
      this.creditCard = creditCard;
      this.ssn = ssn;
    }

    public String getUsername() {
      return username;
    }

    public String getPassword() {
      return password;
    }

    public String getCreditCard() {
      return creditCard;
    }

    public String getSsn() {
      return ssn;
    }
  }

  /** Nested object with sensitive data. */
  static class Account {
    private Long id;
    @Sensitive private SensitiveUser owner;

    Account(Long id, SensitiveUser owner) {
      this.id = id;
      this.owner = owner;
    }

    public Long getId() {
      return id;
    }

    public SensitiveUser getOwner() {
      return owner;
    }
  }

  /** Object with collection of sensitive items. */
  static class UserBatch {
    private String batchName;
    private List<SensitiveUser> users;

    UserBatch(String batchName, List<SensitiveUser> users) {
      this.batchName = batchName;
      this.users = users;
    }

    public String getBatchName() {
      return batchName;
    }

    public List<SensitiveUser> getUsers() {
      return users;
    }
  }

  /** Object with various data types. */
  static class MixedTypes {
    private String name;
    private BigDecimal amount;
    private LocalDate date;
    private LocalDateTime timestamp;
    private boolean active;

    MixedTypes(
        String name, BigDecimal amount, LocalDate date, LocalDateTime timestamp, boolean active) {
      this.name = name;
      this.amount = amount;
      this.date = date;
      this.timestamp = timestamp;
      this.active = active;
    }

    public String getName() {
      return name;
    }

    public BigDecimal getAmount() {
      return amount;
    }

    public LocalDate getDate() {
      return date;
    }

    public LocalDateTime getTimestamp() {
      return timestamp;
    }

    public boolean isActive() {
      return active;
    }
  }

  /** Object with null sensitive field. */
  static class UserWithNullPassword {
    private String username;
    @Sensitive private String password;

    UserWithNullPassword(String username, String password) {
      this.username = username;
      this.password = password;
    }

    public String getUsername() {
      return username;
    }

    public String getPassword() {
      return password;
    }
  }

  /** Object that causes circular reference. */
  @JsonIgnoreProperties({"parent"})
  static class CircularNode {
    private String name;
    private CircularNode parent;

    CircularNode(String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }

    public CircularNode getParent() {
      return parent;
    }

    public void setParent(CircularNode parent) {
      this.parent = parent;
    }
  }

  // ==================== toJson() Tests ====================

  @Test
  void shouldSerializeSimpleObjectToJson() {
    var user = new SimpleUser("John Doe", 30);

    var json = SafeLogger.toJson(user);

    assertNotNull(json);
    assertTrue(json.contains("\"name\" : \"John Doe\""));
    assertTrue(json.contains("\"age\" : 30"));
  }

  @Test
  void shouldMaskSensitiveFieldsInJson() {
    var user = new SensitiveUser("alice", "secret123", "1234567890123456", "123-45-6789");

    var json = SafeLogger.toJson(user);

    assertNotNull(json);
    assertTrue(json.contains("\"username\" : \"alice\""));
    assertTrue(json.contains("\"password\" : \"********\""));
    assertTrue(json.contains("\"creditCard\" : \"************3456\""));
    assertTrue(json.contains("\"ssn\" : \"########\""));
  }

  @Test
  void shouldHandleNullObject() {
    var json = SafeLogger.toJson(null);

    assertEquals("null", json);
  }

  @Test
  void shouldHandleEmptyObject() {
    var emptyMap = new HashMap<>();

    var json = SafeLogger.toJson(emptyMap);

    assertNotNull(json);
    // Pretty printing is enabled, so empty object is formatted with space
    // Use trim to avoid Checkstyle warning about empty blocks in string literals
    assertEquals("{}", json.replaceAll("\\s+", ""));
  }

  @Test
  void shouldHandleNullSensitiveField() {
    var user = new UserWithNullPassword("bob", null);

    var json = SafeLogger.toJson(user);

    assertNotNull(json);
    assertTrue(json.contains("\"username\" : \"bob\""));
    assertTrue(json.contains("\"password\" : null"));
  }

  @Test
  void shouldHandleNestedSensitiveObjects() {
    var user = new SensitiveUser("charlie", "pass456", "9876543210987654", "987-65-4321");
    var account = new Account(12345L, user);

    var json = SafeLogger.toJson(account);

    assertNotNull(json);
    assertTrue(json.contains("\"id\" : 12345"));
    // When @Sensitive is on an object field, the entire object gets masked as "********"
    assertTrue(json.contains("\"owner\" : \"********\""));
  }

  @Test
  void shouldHandleCollectionsWithSensitiveData() {
    var users = new ArrayList<SensitiveUser>();
    users.add(new SensitiveUser("user1", "pass1", "1111222233334444", "111-11-1111"));
    users.add(new SensitiveUser("user2", "pass2", "5555666677778888", "222-22-2222"));

    var batch = new UserBatch("Monthly Batch", users);

    var json = SafeLogger.toJson(batch);

    assertNotNull(json);
    assertTrue(json.contains("\"batchName\" : \"Monthly Batch\""));
    assertTrue(json.contains("\"password\" : \"********\""));
    assertTrue(json.contains("\"creditCard\" : \"************4444\""));
    assertTrue(json.contains("\"creditCard\" : \"************8888\""));
  }

  @Test
  void shouldHandleVariousDataTypes() {
    var obj =
        new MixedTypes(
            "Test",
            new BigDecimal("123.45"),
            LocalDate.of(2024, 1, 15),
            LocalDateTime.of(2024, 1, 15, 10, 30, 0),
            true);

    var json = SafeLogger.toJson(obj);

    assertNotNull(json);
    assertTrue(json.contains("\"name\" : \"Test\""));
    assertTrue(json.contains("\"amount\" : 123.45"));
    assertTrue(json.contains("\"date\" : \"2024-01-15\""));
    assertTrue(json.contains("\"timestamp\" : \"2024-01-15T10:30:00\""));
    assertTrue(json.contains("\"active\" : true"));
  }

  @Test
  void shouldHandleCircularReferenceGracefully() {
    var node = new CircularNode("Node1");
    node.setParent(node); // Circular reference

    var json = SafeLogger.toJson(node);

    // Should not throw exception, @JsonIgnoreProperties prevents circular reference
    assertNotNull(json);
    assertTrue(json.contains("\"name\" : \"Node1\""));
  }

  @Test
  void shouldReturnEmptyJsonOnSerializationFailure() {
    // Create an object that will fail to serialize
    Object problematicObject =
        new Object() {
          @SuppressWarnings("unused")
          public String getValue() {
            throw new RuntimeException("Intentional serialization failure");
          }
        };

    var json = SafeLogger.toJson(problematicObject);

    // Should return empty JSON object instead of throwing
    assertEquals("{}", json);
  }

  // ==================== mask(String, int) Tests ====================

  @Test
  void shouldMaskStringCompletely() {
    var result = SafeLogger.mask("password123", 0);

    assertEquals("********", result);
  }

  @Test
  void shouldMaskStringShowingLastFourCharacters() {
    var result = SafeLogger.mask("1234567890", 4);

    assertEquals("******7890", result);
  }

  @Test
  void shouldMaskStringShowingLastTwoCharacters() {
    var result = SafeLogger.mask("secret", 2);

    assertEquals("****et", result);
  }

  @Test
  void shouldHandleShowLastGreaterThanStringLength() {
    var result = SafeLogger.mask("abc", 10);

    assertEquals("***", result);
  }

  @Test
  void shouldHandleShowLastEqualToStringLength() {
    var result = SafeLogger.mask("test", 4);

    assertEquals("****", result);
  }

  @Test
  void shouldHandleSingleCharacterString() {
    var result = SafeLogger.mask("x", 0);

    assertEquals("********", result);
  }

  @Test
  void shouldHandleEmptyStringInMask() {
    var result = SafeLogger.mask("", 0);

    assertEquals("", result);
  }

  @Test
  void shouldHandleNullStringInMask() {
    var result = SafeLogger.mask(null, 4);

    assertEquals(null, result);
  }

  @Test
  void shouldHandleNegativeShowLast() {
    // Negative showLast should be treated as 0 (complete masking)
    var result = SafeLogger.mask("password", -5);

    assertEquals("********", result);
  }

  @Test
  void shouldHandleVeryLongString() {
    var longString = "a".repeat(1000);

    var result = SafeLogger.mask(longString, 4);

    assertEquals("*".repeat(996) + "aaaa", result);
  }

  // ==================== mask(String) Overload Tests ====================

  @Test
  void shouldMaskStringCompletelyUsingOverload() {
    var result = SafeLogger.mask("confidential");

    assertEquals("********", result);
  }

  @Test
  void shouldHandleNullInOverload() {
    var result = SafeLogger.mask(null);

    assertEquals(null, result);
  }

  @Test
  void shouldHandleEmptyStringInOverload() {
    var result = SafeLogger.mask("");

    assertEquals("", result);
  }

  // ==================== Thread Safety Tests ====================

  @Test
  void shouldHandleConcurrentToJsonCalls() throws InterruptedException {
    int threadCount = 10;
    int iterationsPerThread = 100;
    var executor = Executors.newFixedThreadPool(threadCount);
    var latch = new CountDownLatch(threadCount);
    var successCount = new AtomicInteger(0);

    for (int i = 0; i < threadCount; i++) {
      final int threadId = i;
      executor.submit(
          () -> {
            try {
              for (int j = 0; j < iterationsPerThread; j++) {
                var user =
                    new SensitiveUser(
                        "user" + threadId, "pass" + threadId, "1234567890123456", "123-45-6789");
                var json = SafeLogger.toJson(user);

                if (json != null && json.contains("\"password\" : \"********\"")) {
                  successCount.incrementAndGet();
                }
              }
            } finally {
              latch.countDown();
            }
          });
    }

    latch.await(10, TimeUnit.SECONDS);
    executor.shutdown();

    assertEquals(threadCount * iterationsPerThread, successCount.get());
  }

  @Test
  void shouldHandleConcurrentMaskCalls() throws InterruptedException {
    int threadCount = 10;
    int iterationsPerThread = 100;
    var executor = Executors.newFixedThreadPool(threadCount);
    var latch = new CountDownLatch(threadCount);
    var successCount = new AtomicInteger(0);

    for (int i = 0; i < threadCount; i++) {
      executor.submit(
          () -> {
            try {
              for (int j = 0; j < iterationsPerThread; j++) {
                var result = SafeLogger.mask("password123", 3);
                if ("********123".equals(result)) {
                  successCount.incrementAndGet();
                }
              }
            } finally {
              latch.countDown();
            }
          });
    }

    latch.await(10, TimeUnit.SECONDS);
    executor.shutdown();

    assertEquals(threadCount * iterationsPerThread, successCount.get());
  }

  // ==================== Edge Cases ====================

  @Test
  void shouldHandleMapWithSensitiveValues() {
    Map<String, Object> data = new HashMap<>();
    data.put("user", "admin");
    data.put(
        "credentials", new SensitiveUser("admin", "secret", "4111111111111111", "999-99-9999"));

    var json = SafeLogger.toJson(data);

    assertNotNull(json);
    assertTrue(json.contains("\"user\" : \"admin\""));
    assertTrue(json.contains("\"password\" : \"********\""));
  }

  @Test
  void shouldHandleArrayOfSensitiveObjects() {
    var users =
        new SensitiveUser[] {
          new SensitiveUser("user1", "pass1", "1111222233334444", "111-11-1111"),
          new SensitiveUser("user2", "pass2", "5555666677778888", "222-22-2222")
        };

    var json = SafeLogger.toJson(users);

    assertNotNull(json);
    assertTrue(json.contains("\"password\" : \"********\""));
    assertTrue(json.contains("\"creditCard\" : \"************4444\""));
    assertTrue(json.contains("\"creditCard\" : \"************8888\""));
  }

  @Test
  void shouldPreserveJsonFormatting() {
    var user = new SimpleUser("Alice", 25);

    var json = SafeLogger.toJson(user);

    // Should use pretty printing with indentation
    assertTrue(json.contains("\n"));
    assertTrue(json.contains("  \"name\""));
  }

  @Test
  void shouldHandleShowLastWithSpaces() {
    var result = SafeLogger.mask("my password", 4);

    assertEquals("*******word", result);
  }

  @Test
  void shouldHandleShowLastWithSpecialCharacters() {
    var result = SafeLogger.mask("pass@word#123", 3);

    assertEquals("**********123", result);
  }
}

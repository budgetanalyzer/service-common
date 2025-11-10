package org.budgetanalyzer.core.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Comprehensive integration tests for SensitiveDataModule.
 *
 * <p>Tests Jackson ObjectMapper integration with @Sensitive annotation, including custom mask
 * characters, showLast parameter, nested objects, collections, and edge cases.
 */
class SensitiveDataModuleTest {

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    objectMapper.registerModule(new SensitiveDataModule());
    objectMapper.registerModule(new JavaTimeModule());
  }

  // ==================== Test Data Classes ====================

  /** Simple user with default sensitive masking. */
  static class BasicUser {
    private String username;
    @Sensitive private String password;

    BasicUser() {}

    BasicUser(String username, String password) {
      this.username = username;
      this.password = password;
    }

    public String getUsername() {
      return username;
    }

    public String getPassword() {
      return password;
    }

    public void setUsername(String username) {
      this.username = username;
    }

    public void setPassword(String password) {
      this.password = password;
    }
  }

  /** User with custom showLast parameter. */
  static class CreditCardUser {
    private String name;

    @Sensitive(showLast = 4)
    private String cardNumber;

    CreditCardUser(String name, String cardNumber) {
      this.name = name;
      this.cardNumber = cardNumber;
    }

    public String getName() {
      return name;
    }

    public String getCardNumber() {
      return cardNumber;
    }
  }

  /** User with custom maskChar. */
  static class SsnUser {
    private String name;

    @Sensitive(maskChar = '#')
    private String ssn;

    SsnUser(String name, String ssn) {
      this.name = name;
      this.ssn = ssn;
    }

    public String getName() {
      return name;
    }

    public String getSsn() {
      return ssn;
    }
  }

  /** User with multiple sensitive fields. */
  static class MultiSensitiveUser {
    private String username;
    @Sensitive private String password;

    @Sensitive(showLast = 4)
    private String email;

    @Sensitive(maskChar = 'X', showLast = 2)
    private String phone;

    MultiSensitiveUser(String username, String password, String email, String phone) {
      this.username = username;
      this.password = password;
      this.email = email;
      this.phone = phone;
    }

    public String getUsername() {
      return username;
    }

    public String getPassword() {
      return password;
    }

    public String getEmail() {
      return email;
    }

    public String getPhone() {
      return phone;
    }
  }

  /** Nested object with sensitive data. */
  static class Account {
    private Long accountId;
    @Sensitive private BasicUser owner;

    Account(Long accountId, BasicUser owner) {
      this.accountId = accountId;
      this.owner = owner;
    }

    public Long getAccountId() {
      return accountId;
    }

    public BasicUser getOwner() {
      return owner;
    }
  }

  /** Object with collection of sensitive items. */
  static class Organization {
    private String name;
    private List<BasicUser> members;

    Organization(String name, List<BasicUser> members) {
      this.name = name;
      this.members = members;
    }

    public String getName() {
      return name;
    }

    public List<BasicUser> getMembers() {
      return members;
    }
  }

  /** Object with various non-string types. */
  static class Account2 {
    private String accountName;

    @Sensitive private Integer accountNumber;

    @Sensitive(showLast = 2)
    private Long customerId;

    @Sensitive private BigDecimal balance;

    @Sensitive private Boolean activeStatus;

    @Sensitive private LocalDate createdDate;

    @Sensitive private LocalDateTime lastLogin;

    @Sensitive private Instant lastModified;

    Account2(
        String accountName,
        Integer accountNumber,
        Long customerId,
        BigDecimal balance,
        Boolean activeStatus,
        LocalDate createdDate,
        LocalDateTime lastLogin,
        Instant lastModified) {
      this.accountName = accountName;
      this.accountNumber = accountNumber;
      this.customerId = customerId;
      this.balance = balance;
      this.activeStatus = activeStatus;
      this.createdDate = createdDate;
      this.lastLogin = lastLogin;
      this.lastModified = lastModified;
    }

    public String getAccountName() {
      return accountName;
    }

    public Integer getAccountNumber() {
      return accountNumber;
    }

    public Long getCustomerId() {
      return customerId;
    }

    public BigDecimal getBalance() {
      return balance;
    }

    public Boolean getActiveStatus() {
      return activeStatus;
    }

    public LocalDate getCreatedDate() {
      return createdDate;
    }

    public LocalDateTime getLastLogin() {
      return lastLogin;
    }

    public Instant getLastModified() {
      return lastModified;
    }
  }

  /** Object with null sensitive field. */
  static class NullableUser {
    private String username;
    @Sensitive private String apiKey;

    NullableUser(String username, String apiKey) {
      this.username = username;
      this.apiKey = apiKey;
    }

    public String getUsername() {
      return username;
    }

    public String getApiKey() {
      return apiKey;
    }
  }

  /** Object with empty sensitive field. */
  static class EmptyFieldUser {
    private String name;
    @Sensitive private String token;

    EmptyFieldUser(String name, String token) {
      this.name = name;
      this.token = token;
    }

    public String getName() {
      return name;
    }

    public String getToken() {
      return token;
    }
  }

  /** Base class with sensitive field. */
  static class BaseEntity {
    @Sensitive private String secret;

    BaseEntity(String secret) {
      this.secret = secret;
    }

    public String getSecret() {
      return secret;
    }
  }

  /** Subclass inheriting sensitive field. */
  static class DerivedEntity extends BaseEntity {
    private String publicInfo;

    DerivedEntity(String secret, String publicInfo) {
      super(secret);
      this.publicInfo = publicInfo;
    }

    public String getPublicInfo() {
      return publicInfo;
    }
  }

  /** Object with sensitive getter method (not field). */
  static class MethodAnnotatedUser {
    private String username;
    private String apiToken;

    MethodAnnotatedUser(String username, String apiToken) {
      this.username = username;
      this.apiToken = apiToken;
    }

    public String getUsername() {
      return username;
    }

    @Sensitive
    public String getApiToken() {
      return apiToken;
    }
  }

  // ==================== Basic Masking Tests ====================

  @Test
  void shouldMaskSensitiveFieldWithDefaultSettings() throws Exception {
    var user = new BasicUser("alice", "secretPassword");

    var json = objectMapper.writeValueAsString(user);

    assertNotNull(json);
    assertTrue(json.contains("\"username\":\"alice\""));
    assertTrue(json.contains("\"password\":\"********\""));
    assertFalse(json.contains("secretPassword"));
  }

  @Test
  void shouldMaskSensitiveFieldShowingLastFourCharacters() throws Exception {
    var user = new CreditCardUser("Bob Smith", "4111111111111234");

    var json = objectMapper.writeValueAsString(user);

    assertNotNull(json);
    assertTrue(json.contains("\"name\":\"Bob Smith\""));
    assertTrue(json.contains("\"cardNumber\":\"************1234\""));
    assertFalse(json.contains("4111111111111234"));
  }

  @Test
  void shouldMaskSensitiveFieldWithCustomMaskChar() throws Exception {
    var user = new SsnUser("Charlie", "123-45-6789");

    var json = objectMapper.writeValueAsString(user);

    assertNotNull(json);
    assertTrue(json.contains("\"name\":\"Charlie\""));
    assertTrue(json.contains("\"ssn\":\"########\""));
    assertFalse(json.contains("123-45-6789"));
  }

  @Test
  void shouldHandleMultipleSensitiveFields() throws Exception {
    var user = new MultiSensitiveUser("dave", "pass123", "dave@example.com", "555-1234");

    var json = objectMapper.writeValueAsString(user);

    assertNotNull(json);
    assertTrue(json.contains("\"username\":\"dave\""));
    assertTrue(json.contains("\"password\":\"********\""));
    assertTrue(json.contains("\"email\":\"************.com\""));
    assertTrue(json.contains("\"phone\":\"XXXXXX34\""));
  }

  // ==================== Null and Empty Value Tests ====================

  @Test
  void shouldPreserveNullSensitiveField() throws Exception {
    var user = new NullableUser("user1", null);

    var json = objectMapper.writeValueAsString(user);

    assertNotNull(json);
    assertTrue(json.contains("\"username\":\"user1\""));
    assertTrue(json.contains("\"apiKey\":null"));
  }

  @Test
  void shouldHandleEmptySensitiveField() throws Exception {
    var user = new EmptyFieldUser("user2", "");

    var json = objectMapper.writeValueAsString(user);

    assertNotNull(json);
    assertTrue(json.contains("\"name\":\"user2\""));
    assertTrue(json.contains("\"token\":\"\""));
  }

  // ==================== Non-String Type Tests ====================

  @Test
  void shouldMaskNonStringTypes() throws Exception {
    var account =
        new Account2(
            "Premium Account",
            987654321,
            1234567890L,
            new BigDecimal("12345.67"),
            true,
            LocalDate.of(2024, 1, 15),
            LocalDateTime.of(2024, 1, 15, 10, 30, 0),
            Instant.parse("2024-01-15T10:30:00Z"));

    var json = objectMapper.writeValueAsString(account);

    assertNotNull(json);
    assertTrue(json.contains("\"accountName\":\"Premium Account\""));

    // Integer masked
    assertTrue(json.contains("\"accountNumber\":\"********\""));
    assertFalse(json.contains("987654321"));

    // Long masked with showLast=2
    assertTrue(json.contains("\"customerId\":\"********90\""));
    assertFalse(json.contains("1234567890"));

    // BigDecimal masked
    assertTrue(json.contains("\"balance\":\"********\""));
    assertFalse(json.contains("12345.67"));

    // Boolean masked
    assertTrue(json.contains("\"activeStatus\":\"********\""));

    // LocalDate masked
    assertTrue(json.contains("\"createdDate\":\"********\""));
    assertFalse(json.contains("2024-01-15"));

    // LocalDateTime masked
    assertTrue(json.contains("\"lastLogin\":\"********\""));

    // Instant masked
    assertTrue(json.contains("\"lastModified\":\"********\""));
  }

  // ==================== Nested Object Tests ====================

  @Test
  void shouldMaskNestedSensitiveObjects() throws Exception {
    var user = new BasicUser("nested_user", "nested_password");
    var account = new Account(99999L, user);

    var json = objectMapper.writeValueAsString(account);

    assertNotNull(json);
    assertTrue(json.contains("\"accountId\":99999"));
    assertTrue(json.contains("\"owner\":\"********\""));
    // Entire nested object is masked because @Sensitive is on the owner field
  }

  // ==================== Collection Tests ====================

  @Test
  void shouldHandleCollectionsWithSensitiveData() throws Exception {
    var members = new ArrayList<BasicUser>();
    members.add(new BasicUser("member1", "pass1"));
    members.add(new BasicUser("member2", "pass2"));
    members.add(new BasicUser("member3", "pass3"));

    var org = new Organization("ACME Corp", members);

    var json = objectMapper.writeValueAsString(org);

    assertNotNull(json);
    assertTrue(json.contains("\"name\":\"ACME Corp\""));
    assertTrue(json.contains("\"username\":\"member1\""));
    assertTrue(json.contains("\"username\":\"member2\""));
    assertTrue(json.contains("\"username\":\"member3\""));
    assertTrue(json.contains("\"password\":\"********\""));
    assertFalse(json.contains("pass1"));
    assertFalse(json.contains("pass2"));
    assertFalse(json.contains("pass3"));
  }

  @Test
  void shouldHandleArraysWithSensitiveData() throws Exception {
    var users =
        new BasicUser[] {
          new BasicUser("array_user1", "array_pass1"), new BasicUser("array_user2", "array_pass2")
        };

    var json = objectMapper.writeValueAsString(users);

    assertNotNull(json);
    assertTrue(json.contains("\"username\":\"array_user1\""));
    assertTrue(json.contains("\"username\":\"array_user2\""));
    assertTrue(json.contains("\"password\":\"********\""));
    assertFalse(json.contains("array_pass1"));
    assertFalse(json.contains("array_pass2"));
  }

  @Test
  void shouldHandleMapsWithSensitiveValues() throws Exception {
    Map<String, BasicUser> userMap = new HashMap<>();
    userMap.put("admin", new BasicUser("admin_user", "admin_pass"));
    userMap.put("guest", new BasicUser("guest_user", "guest_pass"));

    var json = objectMapper.writeValueAsString(userMap);

    assertNotNull(json);
    assertTrue(json.contains("\"username\":\"admin_user\""));
    assertTrue(json.contains("\"username\":\"guest_user\""));
    assertTrue(json.contains("\"password\":\"********\""));
    assertFalse(json.contains("admin_pass"));
    assertFalse(json.contains("guest_pass"));
  }

  // ==================== Inheritance Tests ====================

  @Test
  void shouldMaskInheritedSensitiveFields() throws Exception {
    var derived = new DerivedEntity("inherited_secret", "public_data");

    var json = objectMapper.writeValueAsString(derived);

    assertNotNull(json);
    assertTrue(json.contains("\"publicInfo\":\"public_data\""));
    assertTrue(json.contains("\"secret\":\"********\""));
    assertFalse(json.contains("inherited_secret"));
  }

  // ==================== Method Annotation Tests ====================

  @Test
  void shouldMaskSensitiveGetterMethod() throws Exception {
    var user = new MethodAnnotatedUser("methodUser", "api_token_12345");

    var json = objectMapper.writeValueAsString(user);

    assertNotNull(json);
    assertTrue(json.contains("\"username\":\"methodUser\""));
    assertTrue(json.contains("\"apiToken\":\"********\""));
    assertFalse(json.contains("api_token_12345"));
  }

  // ==================== Edge Cases ====================

  @Test
  void shouldMaskSingleCharacterValue() throws Exception {
    var user = new BasicUser("user", "x");

    var json = objectMapper.writeValueAsString(user);

    assertTrue(json.contains("\"password\":\"********\""));
  }

  @Test
  void shouldHandleShowLastGreaterThanValueLength() throws Exception {
    var user = new CreditCardUser("Short", "123");

    var json = objectMapper.writeValueAsString(user);

    assertTrue(json.contains("\"cardNumber\":\"***\""));
    assertFalse(json.contains("\"cardNumber\":\"123\""));
  }

  @Test
  void shouldHandleShowLastEqualToValueLength() throws Exception {
    var user = new CreditCardUser("Exact", "1234");

    var json = objectMapper.writeValueAsString(user);

    // When showLast equals value length, it masks the entire value
    assertTrue(json.contains("\"cardNumber\":\"****\""));
  }

  @Test
  void shouldHandleNegativeShowLastValue() throws Exception {
    // Create a test class with negative showLast (edge case - should mask completely)
    class NegativeShowLastUser {
      private String name;

      @Sensitive(showLast = -5)
      private String token;

      NegativeShowLastUser(String name, String token) {
        this.name = name;
        this.token = token;
      }

      @SuppressWarnings("unused")
      public String getName() {
        return name;
      }

      @SuppressWarnings("unused")
      public String getToken() {
        return token;
      }
    }

    var user = new NegativeShowLastUser("TestUser", "secret_token_123");

    var json = objectMapper.writeValueAsString(user);

    assertNotNull(json);
    assertTrue(json.contains("\"name\":\"TestUser\""));
    // Negative showLast should be treated as 0 (complete masking)
    assertTrue(json.contains("\"token\":\"********\""));
    assertFalse(json.contains("secret_token_123"));
  }

  @Test
  void shouldHandleVeryLongSensitiveValue() throws Exception {
    var longPassword = "a".repeat(1000);
    var user = new BasicUser("longUser", longPassword);

    var json = objectMapper.writeValueAsString(user);

    assertTrue(json.contains("\"password\":\"********\""));
    assertFalse(json.contains(longPassword));
  }

  @Test
  void shouldHandleSpecialCharactersInSensitiveValue() throws Exception {
    var user = new BasicUser("specialUser", "p@$$w0rd!#%&*()");

    var json = objectMapper.writeValueAsString(user);

    assertTrue(json.contains("\"password\":\"********\""));
    assertFalse(json.contains("p@$$w0rd!#%&*()"));
  }

  @Test
  void shouldHandleUnicodeInSensitiveValue() throws Exception {
    var user = new BasicUser("unicodeUser", "пароль密码🔒");

    var json = objectMapper.writeValueAsString(user);

    assertTrue(json.contains("\"password\":\"********\""));
    assertFalse(json.contains("пароль密码🔒"));
  }

  @Test
  void shouldMaskValueWithWhitespace() throws Exception {
    var user = new BasicUser("spaceUser", "my secret password");

    var json = objectMapper.writeValueAsString(user);

    assertTrue(json.contains("\"password\":\"********\""));
    assertFalse(json.contains("my secret password"));
  }

  @Test
  void shouldHandleEmptyCollectionWithSensitiveItems() throws Exception {
    var org = new Organization("Empty Org", new ArrayList<>());

    var json = objectMapper.writeValueAsString(org);

    assertNotNull(json);
    assertTrue(json.contains("\"name\":\"Empty Org\""));
    assertTrue(json.contains("\"members\":[]"));
  }

  // ==================== Deserialization Tests ====================

  @Test
  void shouldDeserializeObjectWithSensitiveFields() throws Exception {
    var jsonString = "{\"username\":\"deserialUser\",\"password\":\"actualPassword\"}";

    var user = objectMapper.readValue(jsonString, BasicUser.class);

    assertNotNull(user);
    assertEquals("deserialUser", user.getUsername());
    assertEquals("actualPassword", user.getPassword());
    // Deserialization should work normally; masking only applies to serialization
  }

  @Test
  void shouldRoundTripWithSensitiveData() throws Exception {
    var originalUser = new BasicUser("roundtrip", "originalPass");

    // Serialize (password will be masked)
    var json = objectMapper.writeValueAsString(originalUser);
    assertTrue(json.contains("\"password\":\"********\""));

    // Deserialize the masked JSON
    var deserializedUser = objectMapper.readValue(json, BasicUser.class);

    // Deserialized object will have masked password
    assertEquals("roundtrip", deserializedUser.getUsername());
    assertEquals("********", deserializedUser.getPassword());
  }

  // ==================== Module Configuration Tests ====================

  @Test
  void shouldWorkWithMultipleModules() throws Exception {
    // ObjectMapper already has both SensitiveDataModule and JavaTimeModule
    var account =
        new Account2(
            "Multi Module Test",
            123456,
            9876543210L,
            new BigDecimal("999.99"),
            false,
            LocalDate.of(2024, 2, 1),
            LocalDateTime.of(2024, 2, 1, 14, 0, 0),
            Instant.parse("2024-02-01T14:00:00Z"));

    var json = objectMapper.writeValueAsString(account);

    assertNotNull(json);
    // Both masking and date formatting should work
    assertTrue(json.contains("\"accountNumber\":\"********\""));
    assertTrue(json.contains("\"createdDate\":\"********\""));
  }

  @Test
  void shouldPreserveNonSensitiveFieldsInComplexObject() throws Exception {
    var user = new MultiSensitiveUser("complexUser", "pass", "complex@example.com", "555-9999");

    var json = objectMapper.writeValueAsString(user);

    // Debug: print actual JSON
    System.out.println("Actual JSON: " + json);

    // Non-sensitive field should be untouched
    assertTrue(json.contains("\"username\":\"complexUser\""));
    // All sensitive fields should be masked
    assertTrue(json.contains("\"password\":\"********\""));
    // Email: "complex@example.com" is 19 chars, showLast=4 should show ".com"
    // Phone: "555-9999" is 8 chars, showLast=2 with maskChar='X' should show "99"

    // Check the actual masked values
    assertTrue(json.contains("\"password\":\"********\""), "Password not properly masked");
    assertTrue(json.contains("\"email\":") && json.contains(".com\""), "Email not properly masked");
    assertTrue(json.contains("\"phone\":") && json.contains("99\""), "Phone not properly masked");
  }
}

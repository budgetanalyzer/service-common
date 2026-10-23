package org.budgetanalyzer.core.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonValue;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.ser.std.StdSerializer;

class SensitiveDataSerializationTest {

  private static final JsonMapper PARSER = JsonMapper.builder().build();

  @Test
  void shouldMaskFieldAnnotatedValueCompletely() throws Exception {
    var account = new FieldAnnotatedAccount("alice", "supersecret");

    var json = SafeLogger.toJson(account);
    var node = PARSER.readTree(json);

    assertThat(node.get("username").asString()).isEqualTo("alice");
    assertThat(node.get("password").asString()).isEqualTo("********");
    assertThat(json).doesNotContain("supersecret");
  }

  @Test
  void shouldMaskGetterAnnotatedValueCompletely() throws Exception {
    var client = new GetterAnnotatedClient("billing-client", "key-abcdef123456");

    var json = SafeLogger.toJson(client);
    var node = PARSER.readTree(json);

    assertThat(node.get("clientName").asString()).isEqualTo("billing-client");
    assertThat(node.get("apiKey").asString()).isEqualTo("********");
    assertThat(json).doesNotContain("abcdef123456");
  }

  @Test
  void shouldUseFixedLengthMaskRegardlessOfValueLengthWhenFullyMasking() throws Exception {
    var shortSecret = PARSER.readTree(SafeLogger.toJson(new FieldAnnotatedAccount("a", "x")));
    var longSecret =
        PARSER.readTree(
            SafeLogger.toJson(new FieldAnnotatedAccount("a", "a-much-longer-secret-value")));

    assertThat(shortSecret.get("password").asString()).isEqualTo("********");
    assertThat(longSecret.get("password").asString()).isEqualTo("********");
  }

  @Test
  void shouldRevealOnlyTrailingCharactersWhenShowLastIsSet() throws Exception {
    var card = new PaymentCard("Visa", "4111222233334444", "secret-pin", 12345678L);

    var json = SafeLogger.toJson(card);
    var node = PARSER.readTree(json);

    assertThat(node.get("brand").asString()).isEqualTo("Visa");
    assertThat(node.get("cardNumber").asString()).isEqualTo("************4444");
    assertThat(json).doesNotContain("411122223333");
  }

  @Test
  void shouldUseCustomMaskCharacterForFullMask() throws Exception {
    var card = new PaymentCard("Visa", "4111222233334444", "secret-pin", 12345678L);

    var json = SafeLogger.toJson(card);
    var node = PARSER.readTree(json);

    assertThat(node.get("pin").asString()).isEqualTo("########");
    assertThat(json).doesNotContain("secret-pin");
  }

  @Test
  void shouldUseCustomMaskCharacterWithShowLastForNonStringValue() throws Exception {
    var card = new PaymentCard("Visa", "4111222233334444", "secret-pin", 12345678L);

    var json = SafeLogger.toJson(card);
    var node = PARSER.readTree(json);

    assertThat(node.get("accountNumber").isString()).isTrue();
    assertThat(node.get("accountNumber").asString()).isEqualTo("XXXXXX78");
    assertThat(json).doesNotContain("123456");
  }

  @Test
  void shouldMaskEntireValueWhenShowLastExceedsLength() throws Exception {
    var card = new PaymentCard("Visa", "123", "1", 7L);

    var json = SafeLogger.toJson(card);
    var node = PARSER.readTree(json);

    assertThat(node.get("cardNumber").asString()).isEqualTo("***");
    assertThat(node.get("accountNumber").asString()).isEqualTo("X");
    assertThat(node.get("pin").asString()).isEqualTo("########");
  }

  @Test
  void shouldMaskEntireValueWhenShowLastEqualsLength() throws Exception {
    var card = new PaymentCard("Visa", "4444", "1", 78L);

    var node = PARSER.readTree(SafeLogger.toJson(card));

    assertThat(node.get("cardNumber").asString()).isEqualTo("****");
    assertThat(node.get("accountNumber").asString()).isEqualTo("XX");
  }

  @Test
  void shouldSerializeNullSensitiveValueAsNull() throws Exception {
    var account = new FieldAnnotatedAccount("alice", null);

    var node = PARSER.readTree(SafeLogger.toJson(account));

    assertThat(node.get("username").asString()).isEqualTo("alice");
    assertThat(node.has("password")).isTrue();
    assertThat(node.get("password").isNull()).isTrue();
  }

  @Test
  void shouldKeepEmptySensitiveValueEmpty() throws Exception {
    var account = new FieldAnnotatedAccount("alice", "");

    var node = PARSER.readTree(SafeLogger.toJson(account));

    assertThat(node.get("username").asString()).isEqualTo("alice");
    assertThat(node.get("password").isString()).isTrue();
    assertThat(node.get("password").asString()).isEmpty();
  }

  @Test
  void shouldMaskRecordComponents() throws Exception {
    var credentials = new Credentials("service-user", "token-abcdef987654");

    var json = SafeLogger.toJson(credentials);
    var node = PARSER.readTree(json);

    assertThat(node.get("principal").asString()).isEqualTo("service-user");
    assertThat(node.get("token").asString()).isEqualTo("**************7654");
    assertThat(json).doesNotContain("abcdef98");
  }

  @Test
  void shouldMaskSensitiveValuesInNestedObjects() throws Exception {
    var profile =
        new UserProfile(
            "usr_1",
            new FieldAnnotatedAccount("alice", "nested-secret"),
            List.of(
                new Credentials("first", "token-first-secret-1111"),
                new Credentials("second", "token-second-secret-2222")));

    var json = SafeLogger.toJson(profile);
    var node = PARSER.readTree(json);

    assertThat(node.get("userId").asString()).isEqualTo("usr_1");
    var account = node.get("account");
    assertThat(account.get("username").asString()).isEqualTo("alice");
    assertThat(account.get("password").asString()).isEqualTo("********");
    var credentials = node.get("credentials");
    assertThat(credentials.isArray()).isTrue();
    assertThat(credentials).hasSize(2);
    assertThat(credentials.get(0).get("principal").asString()).isEqualTo("first");
    assertThat(credentials.get(0).get("token").asString()).isEqualTo("*******************1111");
    assertThat(credentials.get(1).get("principal").asString()).isEqualTo("second");
    assertThat(credentials.get(1).get("token").asString()).isEqualTo("********************2222");
    assertThat(json)
        .doesNotContain("nested-secret")
        .doesNotContain("first-secret")
        .doesNotContain("second-secret");
  }

  @Test
  void shouldMaskSensitiveValuesInObjectsInsideRootList() throws Exception {
    var accounts =
        List.of(
            new FieldAnnotatedAccount("alice", "alice-secret"),
            new FieldAnnotatedAccount("bob", "bob-secret"));

    var json = SafeLogger.toJson(accounts);
    var node = PARSER.readTree(json);

    assertThat(node.isArray()).isTrue();
    assertThat(node).hasSize(2);
    assertThat(node.get(0).get("username").asString()).isEqualTo("alice");
    assertThat(node.get(0).get("password").asString()).isEqualTo("********");
    assertThat(node.get(1).get("username").asString()).isEqualTo("bob");
    assertThat(node.get(1).get("password").asString()).isEqualTo("********");
    assertThat(json).doesNotContain("alice-secret").doesNotContain("bob-secret");
  }

  @Test
  void shouldMaskSensitiveValuesInObjectsInsideMaps() throws Exception {
    var data =
        Map.of(
            "requestId",
            "req_1",
            "client",
            new GetterAnnotatedClient("reporting", "key-map-secret"));

    var json = SafeLogger.toJson(data);
    var node = PARSER.readTree(json);

    assertThat(node.get("requestId").asString()).isEqualTo("req_1");
    assertThat(node.get("client").get("clientName").asString()).isEqualTo("reporting");
    assertThat(node.get("client").get("apiKey").asString()).isEqualTo("********");
    assertThat(json).doesNotContain("key-map-secret");
  }

  @Test
  void shouldOmitEmptySensitiveValueWhenNonEmptyInclusionApplies() throws Exception {
    var json = SafeLogger.toJson(new NonEmptyAccount("alice", ""));
    var node = PARSER.readTree(json);

    assertThat(node.get("username").asString()).isEqualTo("alice");
    assertThat(node.has("password")).isFalse();
  }

  @Test
  void shouldMaskNonEmptySensitiveValueWhenNonEmptyInclusionApplies() throws Exception {
    var json = SafeLogger.toJson(new NonEmptyAccount("alice", "non-empty-secret"));
    var node = PARSER.readTree(json);

    assertThat(node.get("username").asString()).isEqualTo("alice");
    assertThat(node.get("password").asString()).isEqualTo("********");
    assertThat(json).doesNotContain("non-empty-secret");
  }

  @Test
  void shouldMaskSensitiveValuesInArrayShapedBeans() throws Exception {
    var json = SafeLogger.toJson(new ArrayShapedCredentials("service-user", "array-secret-9876"));
    var node = PARSER.readTree(json);

    assertThat(node.isArray()).isTrue();
    assertThat(node).hasSize(2);
    assertThat(node.get(0).asString()).isEqualTo("service-user");
    assertThat(node.get(1).asString()).isEqualTo("*************9876");
    assertThat(json).doesNotContain("array-secret");
  }

  @Test
  void shouldApplyMaskingBeforeOtherPropertySerializer() throws Exception {
    var json = SafeLogger.toJson(new CustomSerializedAccount("alice", "custom-secret"));
    var node = PARSER.readTree(json);

    assertThat(node.get("username").asString()).isEqualTo("ALICE");
    assertThat(node.get("password").asString()).isEqualTo("********");
    assertThat(json).doesNotContain("custom-secret").doesNotContain("CUSTOM-SECRET");
  }

  @Test
  void shouldPreserveJsonValueSerialization() throws Exception {
    var json = SafeLogger.toJson(Map.of("currency", new CurrencyCode("USD")));
    var node = PARSER.readTree(json);

    assertThat(node.get("currency").isString()).isTrue();
    assertThat(node.get("currency").asString()).isEqualTo("USD");
  }

  @Test
  void shouldNotApplyLoggingMaskingToPlainMapper() throws Exception {
    var json =
        JsonMapper.builder().build().writeValueAsString(new FieldAnnotatedAccount("a", "s1"));
    var node = PARSER.readTree(json);

    assertThat(node.get("password").asString()).isEqualTo("s1");
  }

  static class FieldAnnotatedAccount {
    private final String username;

    @Sensitive private final String password;

    FieldAnnotatedAccount(String username, String password) {
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

  static class GetterAnnotatedClient {
    private final String clientName;
    private final String apiKey;

    GetterAnnotatedClient(String clientName, String apiKey) {
      this.clientName = clientName;
      this.apiKey = apiKey;
    }

    public String getClientName() {
      return clientName;
    }

    @Sensitive
    public String getApiKey() {
      return apiKey;
    }
  }

  static class PaymentCard {
    private final String brand;

    @Sensitive(showLast = 4)
    private final String cardNumber;

    @Sensitive(maskChar = '#')
    private final String pin;

    @Sensitive(maskChar = 'X', showLast = 2)
    private final long accountNumber;

    PaymentCard(String brand, String cardNumber, String pin, long accountNumber) {
      this.brand = brand;
      this.cardNumber = cardNumber;
      this.pin = pin;
      this.accountNumber = accountNumber;
    }

    public String getBrand() {
      return brand;
    }

    public String getCardNumber() {
      return cardNumber;
    }

    public String getPin() {
      return pin;
    }

    public long getAccountNumber() {
      return accountNumber;
    }
  }

  record Credentials(String principal, @Sensitive(showLast = 4) String token) {}

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  record NonEmptyAccount(String username, @Sensitive String password) {}

  @JsonFormat(shape = JsonFormat.Shape.ARRAY)
  @JsonPropertyOrder({"principal", "token"})
  record ArrayShapedCredentials(String principal, @Sensitive(showLast = 4) String token) {}

  record CustomSerializedAccount(
      @JsonSerialize(using = UpperCaseSerializer.class) String username,
      @Sensitive @JsonSerialize(using = UpperCaseSerializer.class) String password) {}

  record CurrencyCode(@JsonValue String code) {}

  static class UpperCaseSerializer extends StdSerializer<String> {

    UpperCaseSerializer() {
      super(String.class);
    }

    @Override
    public void serialize(String value, JsonGenerator generator, SerializationContext context) {
      generator.writeString(value.toUpperCase(Locale.ROOT));
    }
  }

  record UserProfile(String userId, FieldAnnotatedAccount account, List<Credentials> credentials) {}
}

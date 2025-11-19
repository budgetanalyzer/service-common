package org.budgetanalyzer.service.security.test;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Test security configuration that provides a mock JWT decoder for integration tests.
 *
 * <p><b>⚠️ FOR TESTING ONLY</b> - This class provides mock JWT infrastructure for integration
 * tests. Do NOT use in production code or enable component scanning on this package.
 *
 * <p><b>Usage:</b> Import this configuration in your integration tests:
 *
 * <pre>{@code
 * @SpringBootTest
 * @Import(TestSecurityConfig.class)
 * class MyIntegrationTest {
 *   // Tests can now use mock JWT authentication
 * }
 * }</pre>
 *
 * <p>The mock decoder returns either:
 *
 * <ul>
 *   <li>A custom JWT set via {@code TestSecurityConfig.CUSTOM_JWT.set(jwt)}
 *   <li>A default test JWT with standard claims (for most tests)
 * </ul>
 *
 * <p><b>Advanced usage:</b> For custom JWT tokens in tests, use {@link JwtTestBuilder}:
 *
 * <pre>{@code
 * @Test
 * void testWithCustomUser() {
 *   Jwt jwt = JwtTestBuilder.user("john-doe")
 *       .withScopes("read:data", "write:data")
 *       .build();
 *   TestSecurityConfig.CUSTOM_JWT.set(jwt);
 *
 *   // Make authenticated request...
 *
 *   TestSecurityConfig.CUSTOM_JWT.remove(); // Cleanup
 * }
 * }</pre>
 *
 * <p><b>Note:</b> This class does NOT have {@code @TestConfiguration} annotation, making it
 * suitable for inclusion in published libraries. Consuming services import it explicitly via
 * {@code @Import}.
 *
 * @see JwtTestBuilder
 */
public class TestSecurityConfig {

  /** ThreadLocal storage for custom JWT tokens. Used by consuming services' test classes. */
  public static final ThreadLocal<Jwt> CUSTOM_JWT = new ThreadLocal<>();

  /**
   * Provides a mock JWT decoder that returns test JWTs.
   *
   * <p>The {@code @Primary} annotation ensures this bean takes precedence over any production
   * {@code JwtDecoder} beans when this configuration is imported. Combined with
   * {@code @ConditionalOnMissingBean} on the production decoder, this ensures only the mock decoder
   * is created in tests.
   *
   * @return mock JWT decoder
   */
  @Bean
  @Primary
  public JwtDecoder jwtDecoder() {
    var mockDecoder = mock(JwtDecoder.class);

    // Return custom JWT if set, otherwise return default JWT
    when(mockDecoder.decode(anyString()))
        .thenAnswer(
            invocation -> {
              Jwt customJwt = CUSTOM_JWT.get();
              return customJwt != null ? customJwt : JwtTestBuilder.defaultJwt();
            });

    return mockDecoder;
  }
}

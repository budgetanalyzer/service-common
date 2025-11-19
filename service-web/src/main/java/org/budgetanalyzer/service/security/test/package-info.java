/**
 * Test utilities for OAuth2 security integration tests.
 *
 * <p><b>⚠️ FOR TESTING ONLY</b> - Classes in this package provide mock JWT infrastructure for
 * integration tests. Do NOT use in production code or enable component scanning on this package.
 *
 * <p><b>Key Components:</b>
 *
 * <ul>
 *   <li>{@link org.budgetanalyzer.service.security.test.TestSecurityConfig} - Mock JWT decoder
 *       configuration
 *   <li>{@link org.budgetanalyzer.service.security.test.JwtTestBuilder} - Fluent builder for test
 *       JWTs
 * </ul>
 *
 * <p><b>Usage in consuming services:</b>
 *
 * <pre>{@code
 * @SpringBootTest
 * @Import(TestSecurityConfig.class)
 * class MyIntegrationTest {
 *
 *   @Test
 *   void testWithCustomUser() {
 *     Jwt jwt = JwtTestBuilder.user("john-doe")
 *         .withScopes("read:data", "write:data")
 *         .build();
 *     TestSecurityConfig.CUSTOM_JWT.set(jwt);
 *
 *     // Make authenticated request...
 *
 *     TestSecurityConfig.CUSTOM_JWT.remove();
 *   }
 * }
 * }</pre>
 *
 * @see org.budgetanalyzer.service.security.OAuth2ResourceServerSecurityConfig
 */
package org.budgetanalyzer.service.security.test;

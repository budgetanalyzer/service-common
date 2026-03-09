package org.budgetanalyzer.service.security.test;

import org.springframework.context.annotation.Configuration;

/**
 * Test security configuration for claims-header-based authentication.
 *
 * <p><b>FOR TESTING ONLY</b> - Import this configuration in integration tests via {@code @Import}.
 *
 * <p>Since {@link org.budgetanalyzer.service.security.ClaimsHeaderSecurityConfig} has no external
 * properties (unlike the old OAuth2 config needing {@code jwk-set-uri}), this config simply ensures
 * the production security config loads cleanly in test contexts. Test identity is set per-request
 * via claims headers using {@link ClaimsHeaderTestBuilder}.
 *
 * <p><b>Usage:</b>
 *
 * <pre>{@code
 * @SpringBootTest
 * @Import(TestClaimsSecurityConfig.class)
 * class MyIntegrationTest {
 *
 *   @Autowired private MockMvc mockMvc;
 *
 *   @Test
 *   void testWithDefaultUser() throws Exception {
 *     mockMvc.perform(get("/api/resource")
 *         .with(ClaimsHeaderTestBuilder.defaultUser()))
 *         .andExpect(status().isOk());
 *   }
 * }
 * }</pre>
 *
 * @see ClaimsHeaderTestBuilder
 * @see org.budgetanalyzer.service.security.ClaimsHeaderSecurityConfig
 */
@Configuration
public class TestClaimsSecurityConfig {
  // No mock beans needed — ClaimsHeaderSecurityConfig has no external dependencies.
  // Test identity is set per-request via claims headers using ClaimsHeaderTestBuilder.
}

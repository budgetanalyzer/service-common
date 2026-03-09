/**
 * Test utilities for claims-header-based security integration tests.
 *
 * <p><b>FOR TESTING ONLY</b> - Classes in this package provide mock claims header infrastructure
 * for integration tests. Do NOT use in production code or enable component scanning on this
 * package.
 *
 * <p><b>Key Components:</b>
 *
 * <ul>
 *   <li>{@link org.budgetanalyzer.service.security.test.TestClaimsSecurityConfig} - Test security
 *       configuration
 *   <li>{@link org.budgetanalyzer.service.security.test.ClaimsHeaderTestBuilder} - Fluent builder
 *       for test claims headers
 * </ul>
 *
 * <p><b>Usage in consuming services:</b>
 *
 * <pre>{@code
 * @SpringBootTest
 * @Import(TestClaimsSecurityConfig.class)
 * class MyIntegrationTest {
 *
 *   @Autowired private MockMvc mockMvc;
 *
 *   @Test
 *   void testWithCustomUser() throws Exception {
 *     mockMvc.perform(get("/api/resource")
 *         .with(ClaimsHeaderTestBuilder.user("usr_abc123")
 *             .withPermissions("transactions:read")))
 *         .andExpect(status().isOk());
 *   }
 * }
 * }</pre>
 *
 * @see org.budgetanalyzer.service.security.ClaimsHeaderSecurityConfig
 */
package org.budgetanalyzer.service.security.test;

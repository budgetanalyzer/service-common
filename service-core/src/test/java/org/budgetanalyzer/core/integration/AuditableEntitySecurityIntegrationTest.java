package org.budgetanalyzer.core.integration;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import org.budgetanalyzer.core.integration.fixture.TestAuditableEntity;
import org.budgetanalyzer.core.integration.fixture.TestAuditableRepository;
import org.budgetanalyzer.service.integration.TestApplication;

/**
 * Integration tests for AuditableEntity user tracking fields with Spring Security.
 *
 * <p>Tests verify that createdBy and updatedBy fields are correctly populated from the Spring
 * Security context via the SecurityContextAuditorAware bean.
 */
@SpringBootTest(classes = TestApplication.class)
@Transactional
@DisplayName("AuditableEntity Security Integration Tests")
class AuditableEntitySecurityIntegrationTest {

  private static final String USER_ID_1 = "auth0|507f1f77bcf86cd799439011";
  private static final String USER_ID_2 = "auth0|507f1f77bcf86cd799439022";

  @Autowired private TestAuditableRepository auditableRepository;

  @Autowired private EntityManager entityManager;

  @BeforeEach
  void setUp() {
    auditableRepository.deleteAll();
    SecurityContextHolder.clearContext();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("Should populate createdBy when user is authenticated")
  void shouldPopulateCreatedByWhenAuthenticated() {
    setAuthenticatedUser(USER_ID_1);

    var entity = new TestAuditableEntity("Test Entity");
    var saved = auditableRepository.save(entity);
    entityManager.flush();

    assertThat(saved.getCreatedBy()).isEqualTo(USER_ID_1);
  }

  @Test
  @DisplayName("Should populate updatedBy when user is authenticated")
  void shouldPopulateUpdatedByWhenAuthenticated() {
    setAuthenticatedUser(USER_ID_1);

    var entity = new TestAuditableEntity("Test Entity");
    var saved = auditableRepository.save(entity);
    entityManager.flush();

    assertThat(saved.getUpdatedBy()).isEqualTo(USER_ID_1);
  }

  @Test
  @DisplayName("Should update updatedBy on entity modification")
  void shouldUpdateUpdatedByOnModification() throws InterruptedException {
    // Create entity as user 1
    setAuthenticatedUser(USER_ID_1);
    var entity = new TestAuditableEntity("Original Name");
    final var saved = auditableRepository.save(entity);
    entityManager.flush();

    // Switch to user 2 and update
    Thread.sleep(100); // Ensure timestamp difference
    setAuthenticatedUser(USER_ID_2);
    saved.setName("Modified Name");
    var updated = auditableRepository.save(saved);
    entityManager.flush();

    assertThat(updated.getUpdatedBy()).isEqualTo(USER_ID_2);
  }

  @Test
  @DisplayName("Should maintain createdBy immutability across updates")
  void shouldMaintainCreatedByImmutability() throws InterruptedException {
    // Create entity as user 1
    setAuthenticatedUser(USER_ID_1);
    var entity = new TestAuditableEntity("Original Name");
    final var saved = auditableRepository.save(entity);
    entityManager.flush();
    final var originalCreatedBy = saved.getCreatedBy();

    // Switch to user 2 and update
    Thread.sleep(100);
    setAuthenticatedUser(USER_ID_2);
    saved.setName("Modified Name");
    var updated = auditableRepository.save(saved);
    entityManager.flush();

    // createdBy should remain unchanged
    assertThat(updated.getCreatedBy()).isEqualTo(originalCreatedBy);
    assertThat(updated.getCreatedBy()).isEqualTo(USER_ID_1);
    // updatedBy should reflect the new user
    assertThat(updated.getUpdatedBy()).isEqualTo(USER_ID_2);
  }

  @Test
  @DisplayName("Should have null user fields without authentication")
  void shouldHaveNullUserFieldsWithoutAuthentication() {
    // No authentication set
    SecurityContextHolder.clearContext();

    var entity = new TestAuditableEntity("Test Entity");
    var saved = auditableRepository.save(entity);
    entityManager.flush();

    assertThat(saved.getCreatedBy()).isNull();
    assertThat(saved.getUpdatedBy()).isNull();
  }

  @Test
  @DisplayName("Should track different users on create and update")
  void shouldTrackDifferentUsersOnCreateAndUpdate() throws InterruptedException {
    // Create entity as user 1
    setAuthenticatedUser(USER_ID_1);
    var entity = new TestAuditableEntity("Test Entity");
    var saved = auditableRepository.save(entity);
    entityManager.flush();

    assertThat(saved.getCreatedBy()).isEqualTo(USER_ID_1);
    assertThat(saved.getUpdatedBy()).isEqualTo(USER_ID_1);

    // Update as user 2
    Thread.sleep(100);
    setAuthenticatedUser(USER_ID_2);
    saved.setName("Updated by User 2");
    var updated = auditableRepository.save(saved);
    entityManager.flush();

    // Verify tracking
    assertThat(updated.getCreatedBy()).isEqualTo(USER_ID_1);
    assertThat(updated.getUpdatedBy()).isEqualTo(USER_ID_2);
  }

  @Test
  @DisplayName("Should persist user tracking fields to database")
  void shouldPersistUserTrackingFieldsToDatabase() {
    setAuthenticatedUser(USER_ID_1);

    var entity = new TestAuditableEntity("Test Entity");
    var saved = auditableRepository.save(entity);
    entityManager.flush();
    entityManager.clear(); // Clear persistence context

    // Reload from database
    var reloaded = auditableRepository.findById(saved.getId()).orElseThrow();

    assertThat(reloaded.getCreatedBy()).isEqualTo(USER_ID_1);
    assertThat(reloaded.getUpdatedBy()).isEqualTo(USER_ID_1);
  }

  @Test
  @DisplayName("Should handle email-based user IDs")
  void shouldHandleEmailBasedUserIds() {
    var emailUserId = "user@example.com";
    setAuthenticatedUser(emailUserId);

    var entity = new TestAuditableEntity("Test Entity");
    var saved = auditableRepository.save(entity);
    entityManager.flush();

    assertThat(saved.getCreatedBy()).isEqualTo(emailUserId);
    assertThat(saved.getUpdatedBy()).isEqualTo(emailUserId);
  }

  @Test
  @DisplayName("Should handle multiple entities created by same user")
  void shouldHandleMultipleEntitiesCreatedBySameUser() {
    setAuthenticatedUser(USER_ID_1);

    var entity1 = new TestAuditableEntity("Entity 1");
    var entity2 = new TestAuditableEntity("Entity 2");
    var entity3 = new TestAuditableEntity("Entity 3");

    final var saved1 = auditableRepository.save(entity1);
    final var saved2 = auditableRepository.save(entity2);
    final var saved3 = auditableRepository.save(entity3);
    entityManager.flush();

    assertThat(saved1.getCreatedBy()).isEqualTo(USER_ID_1);
    assertThat(saved2.getCreatedBy()).isEqualTo(USER_ID_1);
    assertThat(saved3.getCreatedBy()).isEqualTo(USER_ID_1);
  }

  @Test
  @DisplayName("Should not populate user fields for anonymous user")
  void shouldNotPopulateUserFieldsForAnonymousUser() {
    // Set anonymous authentication
    var anonymousAuth =
        new UsernamePasswordAuthenticationToken("anonymousUser", null, java.util.List.of());
    SecurityContextHolder.getContext().setAuthentication(anonymousAuth);

    var entity = new TestAuditableEntity("Test Entity");
    var saved = auditableRepository.save(entity);
    entityManager.flush();

    // Anonymous user should result in null (filtered by SecurityContextAuditorAware)
    assertThat(saved.getCreatedBy()).isNull();
    assertThat(saved.getUpdatedBy()).isNull();
  }

  /**
   * Sets an authenticated user in the security context.
   *
   * @param userId the user ID to set
   */
  private void setAuthenticatedUser(String userId) {
    var authentication = new UsernamePasswordAuthenticationToken(userId, null, java.util.List.of());
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }
}

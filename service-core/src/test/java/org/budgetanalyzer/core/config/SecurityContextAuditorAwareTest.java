package org.budgetanalyzer.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.Collections;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class SecurityContextAuditorAwareTest {

  private SecurityContextAuditorAware auditorAware;

  @BeforeEach
  void setUp() {
    auditorAware = new SecurityContextAuditorAware();
    SecurityContextHolder.clearContext();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void shouldReturnUserIdWhenAuthenticated() {
    // Arrange
    String userId = "auth0|123456789";
    Authentication auth =
        new UsernamePasswordAuthenticationToken(userId, "password", Collections.emptyList());
    SecurityContextHolder.getContext().setAuthentication(auth);

    // Act
    var result = auditorAware.getCurrentAuditor();

    // Assert
    assertTrue(result.isPresent(), "Should return auditor when authenticated");
    assertEquals(userId, result.get(), "Should return the user ID from authentication");
  }

  @Test
  void shouldReturnEmptyWhenNoAuthentication() {
    // Arrange - No authentication set (cleared in setUp)

    // Act
    var result = auditorAware.getCurrentAuditor();

    // Assert
    assertFalse(result.isPresent(), "Should return empty when no authentication");
  }

  @Test
  void shouldReturnEmptyWhenNotAuthenticated() {
    // Arrange
    Authentication auth =
        new UsernamePasswordAuthenticationToken("user", "password") {
          @Override
          public boolean isAuthenticated() {
            return false;
          }
        };
    SecurityContextHolder.getContext().setAuthentication(auth);

    // Act
    var result = auditorAware.getCurrentAuditor();

    // Assert
    assertFalse(result.isPresent(), "Should return empty when not authenticated");
  }

  @Test
  void shouldReturnEmptyForAnonymousUser() {
    // Arrange
    Authentication auth =
        new UsernamePasswordAuthenticationToken(
            "anonymousUser", "password", Collections.emptyList());
    SecurityContextHolder.getContext().setAuthentication(auth);

    // Act
    var result = auditorAware.getCurrentAuditor();

    // Assert
    assertFalse(result.isPresent(), "Should return empty for anonymous user");
  }

  @Test
  void shouldHandleCustomAuthentication() {
    // Arrange
    String userId = "user-123";
    Authentication auth =
        new Authentication() {
          @Override
          public Collection<? extends GrantedAuthority> getAuthorities() {
            return Collections.emptyList();
          }

          @Override
          public Object getCredentials() {
            return null;
          }

          @Override
          public Object getDetails() {
            return null;
          }

          @Override
          public Object getPrincipal() {
            return userId;
          }

          @Override
          public boolean isAuthenticated() {
            return true;
          }

          @Override
          public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {}

          @Override
          public String getName() {
            return userId;
          }
        };
    SecurityContextHolder.getContext().setAuthentication(auth);

    // Act
    var result = auditorAware.getCurrentAuditor();

    // Assert
    assertTrue(result.isPresent(), "Should return auditor for custom authentication");
    assertEquals(userId, result.get(), "Should return the user ID from custom authentication");
  }

  @Test
  void shouldHandleJwtStyleUserId() {
    // Arrange - Auth0 style user ID
    String auth0UserId = "auth0|507f1f77bcf86cd799439011";
    Authentication auth =
        new UsernamePasswordAuthenticationToken(
            auth0UserId, "credentials", Collections.emptyList());
    SecurityContextHolder.getContext().setAuthentication(auth);

    // Act
    var result = auditorAware.getCurrentAuditor();

    // Assert
    assertTrue(result.isPresent(), "Should return auditor for JWT-style user ID");
    assertEquals(auth0UserId, result.get(), "Should preserve full Auth0 user ID format");
  }

  @Test
  void shouldHandleEmailAsUserId() {
    // Arrange - Some systems use email as user ID
    String email = "user@example.com";
    Authentication auth =
        new UsernamePasswordAuthenticationToken(email, "password", Collections.emptyList());
    SecurityContextHolder.getContext().setAuthentication(auth);

    // Act
    var result = auditorAware.getCurrentAuditor();

    // Assert
    assertTrue(result.isPresent(), "Should return auditor when using email as user ID");
    assertEquals(email, result.get(), "Should return email as user ID");
  }
}

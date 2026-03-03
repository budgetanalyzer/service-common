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
    var userId = "usr_123456789";
    var auth = new UsernamePasswordAuthenticationToken(userId, "password", Collections.emptyList());
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
    var auth =
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
    var auth =
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
    var userId = "user-123";
    var auth =
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
  void shouldHandleIdpStyleUserId() {
    // Arrange - IdP style user ID
    var idpUserId = "idp|507f1f77bcf86cd799439011";
    var auth =
        new UsernamePasswordAuthenticationToken(idpUserId, "credentials", Collections.emptyList());
    SecurityContextHolder.getContext().setAuthentication(auth);

    // Act
    var result = auditorAware.getCurrentAuditor();

    // Assert
    assertTrue(result.isPresent(), "Should return auditor for JWT-style user ID");
    assertEquals(idpUserId, result.get(), "Should preserve full IdP user ID format");
  }

  @Test
  void shouldHandleEmailAsUserId() {
    // Arrange - Some systems use email as user ID
    var email = "user@example.com";
    var auth = new UsernamePasswordAuthenticationToken(email, "password", Collections.emptyList());
    SecurityContextHolder.getContext().setAuthentication(auth);

    // Act
    var result = auditorAware.getCurrentAuditor();

    // Assert
    assertTrue(result.isPresent(), "Should return auditor when using email as user ID");
    assertEquals(email, result.get(), "Should return email as user ID");
  }
}

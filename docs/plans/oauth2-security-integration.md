# OAuth2 Security Integration into service-web

## Overview
Add OAuth2 resource server security directly to the existing **service-web** module (no separate module). All web services will be OAuth2 resource servers.

## Architecture Decisions

✅ **Integrate into service-web** (not separate service-web-security module)
✅ **Breaking change** - All services adopt OAuth2 simultaneously (lockstep upgrade)
✅ **`api` scope** - OAuth2 transitively included in all service-web consumers
✅ **Test helpers in `src/main/.../security/test/`** - Published with library (no @Configuration annotation)
✅ **Remove local SecurityConfig immediately** when services adopt service-web

---

## Step 1: Add OAuth2 to service-web

### 1.1 Update build.gradle.kts

**File:** `service-web/build.gradle.kts`

Add OAuth2 dependency (api scope for transitive inclusion):
```kotlin
dependencies {
    // Existing dependencies...
    api(project(":service-core"))
    api(libs.spring.boot.starter.web)
    api(libs.springdoc.openapi.starter.webmvc.ui)

    // OAuth2 Resource Server - NEW
    api(libs.spring.boot.starter.oauth2.resource.server)

    // Test dependencies...
}
```

### 1.2 Create Security Components

**Directory structure:**
```
service-web/src/main/java/org/budgetanalyzer/service/security/
├── OAuth2ResourceServerSecurityConfig.java
├── SecurityContextUtil.java
└── test/
    ├── TestSecurityConfig.java (no @Configuration)
    ├── JwtTestBuilder.java
    └── package-info.java (warns this is test-only)
```

**Source files:** Copy from `currency-service`:
- `src/main/java/.../config/SecurityConfig.java` → `OAuth2ResourceServerSecurityConfig.java`
- `src/main/java/.../security/SecurityContextUtil.java` → `SecurityContextUtil.java`
- `src/test/java/.../config/TestSecurityConfig.java` → `security/test/TestSecurityConfig.java`
- `src/test/java/.../base/JwtTestBuilder.java` → `security/test/JwtTestBuilder.java`

**Changes to OAuth2ResourceServerSecurityConfig:**
- Rename class: `SecurityConfig` → `OAuth2ResourceServerSecurityConfig`
- Update package: `org.budgetanalyzer.currency.config` → `org.budgetanalyzer.service.security`
- Keep `@Configuration`
- Keep `@ConditionalOnMissingBean(JwtDecoder.class)` on jwtDecoder() method
- Update Javadoc to reflect generic usage

**Changes to SecurityContextUtil:**
- Update package: `org.budgetanalyzer.currency.security` → `org.budgetanalyzer.service.security`
- Keep all functionality as-is (already generic)

**Changes to TestSecurityConfig:**
- **REMOVE** `@TestConfiguration` annotation (critical!)
- Keep `@Bean` and `@Primary` annotations on jwtDecoder() method
- Update package: `org.budgetanalyzer.currency.config` → `org.budgetanalyzer.service.security.test`
- Make it a **concrete class** (not abstract)
- Add strong Javadoc warnings about test-only usage
- Update import for JwtTestBuilder

**Changes to JwtTestBuilder:**
- Update package: `org.budgetanalyzer.currency.base` → `org.budgetanalyzer.service.security.test`
- Keep all functionality as-is

**Create package-info.java:**
```java
/**
 * Test utilities for OAuth2 security integration tests.
 *
 * <p><strong>FOR TESTING ONLY</strong> - Classes in this package provide mock JWT infrastructure
 * for integration tests. Do NOT use in production code or enable component scanning on this package.
 *
 * @see org.budgetanalyzer.service.security.test.TestSecurityConfig
 * @see org.budgetanalyzer.service.security.test.JwtTestBuilder
 */
package org.budgetanalyzer.service.security.test;
```

### 1.3 Update Auto-Configuration

Ensure component scanning includes security package.

**Check:** `service-web/src/main/resources/META-INF/spring.factories` or auto-configuration class already scans `org.budgetanalyzer.service`

---

## Step 2: Write Tests for service-web

Create comprehensive tests:
- `OAuth2ResourceServerSecurityConfigTest` - Unit tests for configuration
- `SecurityContextUtilTest` - Unit tests for JWT claim extraction
- `JwtTestBuilderTest` - Tests for fluent builder API
- `TestSecurityConfigTest` - Verify mock decoder behavior when imported

---

## Step 3: Build and Publish service-common

```bash
cd /workspace/service-common
./gradlew spotlessApply
./gradlew clean build
./gradlew publishToMavenLocal
```

Verify published:
- `org.budgetanalyzer:service-core:0.0.1-SNAPSHOT`
- `org.budgetanalyzer:service-web:0.0.1-SNAPSHOT` (now with OAuth2)

---

## Step 4: Update currency-service

### 4.1 Update TestContainersConfig

**File:** `currency-service/src/test/java/org/budgetanalyzer/currency/config/TestContainersConfig.java`

**Change line 47:**
```java
// OLD
@Import(TestSecurityConfig.class)

// NEW
@Import(org.budgetanalyzer.service.security.test.TestSecurityConfig.class)
```

### 4.2 Update AbstractIntegrationTest

**File:** `currency-service/src/test/java/org/budgetanalyzer/currency/base/AbstractIntegrationTest.java`

**Update imports (lines 10-11):**
```java
// OLD
import org.budgetanalyzer.currency.config.TestSecurityConfig;

// NEW
import org.budgetanalyzer.service.security.test.TestSecurityConfig;
```

**Keep all other code unchanged** - setCustomJwt(), clearCustomJwt(), etc. all work the same

### 4.3 Delete Local Files

```bash
rm currency-service/src/main/java/org/budgetanalyzer/currency/config/SecurityConfig.java
rm currency-service/src/main/java/org/budgetanalyzer/currency/security/SecurityContextUtil.java
rm currency-service/src/test/java/org/budgetanalyzer/currency/config/TestSecurityConfig.java
rm currency-service/src/test/java/org/budgetanalyzer/currency/base/JwtTestBuilder.java
```

### 4.4 Update Imports Throughout

Search and replace in all test files:
```java
// OLD imports
import org.budgetanalyzer.currency.security.SecurityContextUtil;
import org.budgetanalyzer.currency.base.JwtTestBuilder;

// NEW imports
import org.budgetanalyzer.service.security.SecurityContextUtil;
import org.budgetanalyzer.service.security.test.JwtTestBuilder;
```

Search and replace in all production files:
```java
// OLD import
import org.budgetanalyzer.currency.security.SecurityContextUtil;

// NEW import
import org.budgetanalyzer.service.security.SecurityContextUtil;
```

### 4.5 Build and Test

```bash
cd /workspace/currency-service
./gradlew spotlessApply
./gradlew clean build
```

---

## Step 5: Update transaction-service

### 5.1 Update TransactionServiceApplicationTests

**File:** `transaction-service/src/test/java/org/budgetanalyzer/transaction/TransactionServiceApplicationTests.java`

**Replace inner TestConfig class:**
```java
// DELETE lines 20-41 (entire TestConfiguration static class)

// ADD at top of file
import org.budgetanalyzer.service.security.test.TestSecurityConfig;

// UPDATE @SpringBootTest to @Import
@SpringBootTest
@Import(TestSecurityConfig.class)  // NEW
class TransactionServiceApplicationTests {
  @Test
  void contextLoads() {}
}
```

### 5.2 Delete Local Files

```bash
rm transaction-service/src/main/java/org/budgetanalyzer/transaction/config/SecurityConfig.java
rm transaction-service/src/main/java/org/budgetanalyzer/transaction/security/SecurityContextUtil.java
```

### 5.3 Update Imports Throughout

Search and replace in all production files:
```java
// OLD import
import org.budgetanalyzer.transaction.security.SecurityContextUtil;

// NEW import
import org.budgetanalyzer.service.security.SecurityContextUtil;
```

### 5.4 Build and Test

```bash
cd /workspace/transaction-service
./gradlew spotlessApply
./gradlew clean build
```

---

## Configuration Requirements

Services using service-web must configure:

**application.yml:**
```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${AUTH0_ISSUER_URI}

auth0:
  audience: ${AUTH0_AUDIENCE:https://api.budgetanalyzer.org}
```

---

## Usage Examples

### Consuming service-web (automatic)

**build.gradle.kts:**
```kotlin
dependencies {
    implementation("org.budgetanalyzer:service-web:0.0.1-SNAPSHOT")
    // OAuth2 transitively included
}
```

### Writing integration tests

**Option 1: Using AbstractIntegrationTest (currency-service pattern):**
```java
class MyIntegrationTest extends AbstractIntegrationTest {
    @Test
    void testWithCustomUser() {
        Jwt jwt = JwtTestBuilder.user("john-doe")
            .withScopes("read:data", "write:data")
            .build();
        setCustomJwt(jwt);
        // Test with custom JWT...
    }
}
```

**Option 2: Direct @Import (transaction-service pattern):**
```java
@SpringBootTest
@Import(TestSecurityConfig.class)
class MyTest {
    @Test
    void contextLoads() {}
}
```

---

## Success Criteria

- ✅ service-web builds with OAuth2 security components
- ✅ All service-web tests pass
- ✅ Published to Maven Local
- ✅ currency-service builds and all tests pass
- ✅ transaction-service builds and all tests pass
- ✅ No local SecurityConfig duplication
- ✅ All OAuth2 functionality preserved
- ✅ Test infrastructure working (mock JwtDecoder, JwtTestBuilder)

---

## Documentation Updates

Update after implementation:
1. **service-common/CLAUDE.md** - Add OAuth2 security section
2. **currency-service/CLAUDE.md** - Document OAuth2 usage
3. **transaction-service/CLAUDE.md** - Document OAuth2 usage

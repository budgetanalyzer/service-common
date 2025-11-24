# Versioning and Backwards Compatibility

## Core Principle

**CRITICAL**: ALL changes to **service-core** and **service-web** MUST be backwards compatible. We maintain a common platform across all microservices and upgrade all services in lockstep when we upgrade these libraries.

## Multi-Module Versioning

**service-core** and **service-web** are versioned **together as a coordinated pair**:
- Both modules share the same version number (e.g., `0.0.1-SNAPSHOT`)
- Both modules are released together in lockstep
- Changes to either module trigger a version bump for both
- Consuming services upgrade both modules simultaneously

## Why Backwards Compatibility Matters

### CI/CD-Driven Lockstep Upgrades

Budget Analyzer uses a **CI/CD-driven lockstep upgrade strategy** for service-core and service-web:

**How it works**: Pushes to service-common trigger automated CI/CD releases of all microservices. This is not a manual "war room" coordination - it's fully automated.

**Benefits**:
- All services stay on the same version of both modules - always latest
- Integration issues are caught immediately during the coordinated upgrade
- No version fragmentation across the microservice ecosystem
- No mental overhead of tracking "which service is on which version"

**Why not independent versioning?** Independent versioning creates operational complexity:
- Compatibility matrices to maintain
- "Works on my machine" issues from version mismatches
- Integration bugs that surface weeks after the actual breaking change
- Cognitive overhead of version tracking across services

With CI/CD lockstep, the question "which version?" has one answer: latest.

### Problems with Breaking Changes

Breaking changes undermine the lockstep strategy by:
- **Forcing staggered upgrades**: Services must upgrade one-by-one instead of together
- **Version fragmentation**: Services end up on different versions of the libraries
- **Missed compatibility issues**: Integration problems surface weeks/months later
- **Deployment coordination nightmares**: Complex rollout sequences, rollback challenges
- **Technical debt accumulation**: Temporary workarounds become permanent

## Rules for Backwards Compatibility

### 1. No Breaking API Changes (Without Major Version Bump)

A **breaking change** is any modification that could cause existing code to:
- Fail to compile
- Change behavior unexpectedly
- Throw new exceptions at runtime
- Require code changes in consuming services

**Examples of breaking changes:**
- Removing public methods, classes, or interfaces
- Changing method signatures (parameters, return types)
- Renaming public members
- Changing exception types thrown
- Modifying field visibility (public → protected/private)
- Changing constructor parameters
- Removing enum values
- Changing default behavior of existing methods

### 2. All New Features Must Work With Existing Services

When adding new functionality:
- New methods/classes are fine (additive changes)
- New optional parameters are fine (with sensible defaults)
- New configuration properties must have backwards-compatible defaults
- New exceptions should extend existing exception hierarchy
- Database schema changes must work with old code (see [Flyway Migrations](#database-migrations))

**Pattern: Extend, Don't Modify**
```java
// ❌ BAD: Modifying existing method
public void processTransaction(Transaction tx) {
    // Changed behavior - BREAKING!
}

// ✅ GOOD: Add new method, keep old one
public void processTransaction(Transaction tx) {
    // Original behavior unchanged
}

public void processTransactionWithValidation(Transaction tx, ValidationRules rules) {
    // New functionality
}
```

### 3. Deprecate Before Removal

Never remove public APIs without warning. Follow this deprecation process:

**Step 1: Deprecate (Minor Version Bump)**
```java
/**
 * Processes a transaction.
 *
 * @deprecated Use {@link #processTransactionWithValidation(Transaction, ValidationRules)}
 *             instead. This method will be removed in version 2.0.0.
 */
@Deprecated(since = "1.5.0", forRemoval = true)
public void processTransaction(Transaction tx) {
    // Keep working implementation
}
```

**Step 2: Provide Migration Path**
- Document the replacement in Javadoc
- Add migration examples in CHANGELOG.md
- Update service-common documentation
- Communicate to all service teams

**Step 3: Remove (Major Version Bump)**
- Wait at least one minor version (e.g., deprecate in 1.5.0, remove in 2.0.0)
- Coordinate removal with all consuming services
- Update all services before publishing major version

### 4. Test Against All Consuming Services

Before releasing service-common changes:
- Build and test against transaction-service
- Build and test against currency-service
- Build and test against any other consuming services
- Run integration test suites for each service
- Verify no compilation errors, test failures, or behavior changes

**Testing checklist:**
```bash
# In each consuming service repository
./gradlew clean build
./gradlew test
./gradlew integrationTest

# Check for deprecation warnings
./gradlew build | grep -i "deprecated"

# Verify behavior unchanged
# Run smoke tests or critical path tests
```

### 5. When In Doubt, Add New Rather Than Modify

If you're unsure whether a change is breaking:
- Create a new method/class instead of modifying existing ones
- Add optional parameters with defaults
- Create new configuration properties
- Extend existing classes rather than changing them

**Example: Adding functionality to an exception**
```java
// ❌ RISKY: Changing existing exception
public class BusinessException extends RuntimeException {
    // Adding new required constructor parameter - could break subclasses!
    public BusinessException(String message, ErrorCode code) { }
}

// ✅ SAFE: New exception extends existing
public class BusinessException extends RuntimeException {
    // Original constructors unchanged
    public BusinessException(String message) { }
}

public class CategorizedBusinessException extends BusinessException {
    // New functionality in subclass
    public CategorizedBusinessException(String message, ErrorCode code) { }
}
```

## Semantic Versioning

**service-core** and **service-web** follow [Semantic Versioning 2.0.0](https://semver.org/):

**Important**: Both modules share the same version number and are always released together.

**Version format: MAJOR.MINOR.PATCH** (e.g., 1.4.2)

### PATCH version (1.4.2 → 1.4.3)
- Bug fixes only
- No API changes
- No new features
- 100% backwards compatible

**When to use:**
- Fixing bugs in existing functionality
- Correcting documentation
- Internal refactoring with no external impact
- Dependency patch updates (security fixes)

### MINOR version (1.4.3 → 1.5.0)
- New features added
- New public APIs
- New optional parameters with defaults
- Deprecations announced
- 100% backwards compatible

**When to use:**
- Adding new methods/classes
- Adding optional configuration
- Marking APIs as deprecated (with migration path)
- Adding new exceptions to hierarchy
- Non-breaking database schema additions

### MAJOR version (1.5.0 → 2.0.0)
- Breaking changes allowed
- Deprecated APIs removed
- Behavior changes
- Requires coordinated migration

**When to use:**
- Removing deprecated APIs (after warning period)
- Changing method signatures
- Removing/renaming classes
- Changing default behavior
- Major architectural refactoring

**IMPORTANT**: Major version bumps require:
1. Migration guide for all consuming services
2. Coordinated upgrade plan across all services
3. Testing in all service environments
4. Rollback plan in case of issues

## Database Migrations

Database schema changes require special care for backwards compatibility.

### Safe Schema Changes (Additive)
```sql
-- ✅ Adding new table (doesn't affect existing code)
CREATE TABLE new_feature (
    id BIGSERIAL PRIMARY KEY,
    data VARCHAR(255)
);

-- ✅ Adding nullable column (existing queries still work)
ALTER TABLE transactions
ADD COLUMN metadata JSONB;

-- ✅ Adding index (performance improvement, no breaking change)
CREATE INDEX idx_transactions_date ON transactions(transaction_date);
```

### Unsafe Schema Changes (Breaking)
```sql
-- ❌ Renaming column (breaks existing queries)
ALTER TABLE transactions
RENAME COLUMN amount TO transaction_amount;

-- ❌ Changing column type (may break data access)
ALTER TABLE transactions
ALTER COLUMN amount TYPE DECIMAL(20,4);

-- ❌ Adding NOT NULL column without default (insert failures)
ALTER TABLE transactions
ADD COLUMN required_field VARCHAR(255) NOT NULL;

-- ❌ Dropping column (breaks existing code reading it)
ALTER TABLE transactions
DROP COLUMN legacy_field;
```

### Multi-Step Migration Pattern

For necessary breaking database changes, use a multi-release approach:

**Release N (Add):**
1. Add new column/table (nullable, with defaults)
2. Update code to write to both old and new locations
3. Deploy to all services

**Release N+1 (Migrate):**
1. Backfill data from old to new location
2. Update code to read from new location (still write to both)
3. Deploy and verify

**Release N+2 (Remove):**
1. Stop writing to old location
2. Remove old column/table
3. Deploy to all services

## Examples: What's Breaking vs. Safe

### Method Signature Changes

```java
// ❌ BREAKING: Changed return type
// Before:
public String getTransactionId();
// After:
public UUID getTransactionId();

// ❌ BREAKING: Changed parameter type
// Before:
public void process(String id);
// After:
public void process(UUID id);

// ❌ BREAKING: Added required parameter
// Before:
public void process(Transaction tx);
// After:
public void process(Transaction tx, ValidationMode mode);

// ✅ SAFE: Added overload with new parameter
// Before:
public void process(Transaction tx);
// After:
public void process(Transaction tx);
public void process(Transaction tx, ValidationMode mode);

// ✅ SAFE: Added optional parameter with default
// Before:
public void process(Transaction tx);
// After:
public void process(Transaction tx);
public void process(Transaction tx, ValidationMode mode) {
    // New overload
}
```

### Class and Interface Changes

```java
// ❌ BREAKING: Removed method from interface
public interface TransactionService {
    void create(Transaction tx);
    // void delete(Long id); ← Removed (breaks implementations!)
}

// ❌ BREAKING: Added method without default
public interface TransactionService {
    void create(Transaction tx);
    void validate(Transaction tx); // ← New (breaks existing implementations!)
}

// ✅ SAFE: Added default method to interface
public interface TransactionService {
    void create(Transaction tx);

    default void validate(Transaction tx) {
        // Default implementation
    }
}

// ✅ SAFE: Added new method to abstract class
public abstract class BaseEntity {
    public abstract Long getId();

    // New method with implementation
    public boolean isNew() {
        return getId() == null;
    }
}
```

### Exception Changes

```java
// ❌ BREAKING: Changed exception type
// Before:
public void process() throws BusinessException;
// After:
public void process() throws ServiceException;

// ❌ BREAKING: Added checked exception
// Before:
public void process();
// After:
public void process() throws ValidationException;

// ✅ SAFE: Added unchecked exception to documentation
// Before:
public void process();
// After:
/**
 * @throws ValidationException if validation fails (unchecked)
 */
public void process();

// ✅ SAFE: Made exception hierarchy more specific
// Before: throws Exception
// After: throws BusinessException (more specific, still compatible)
```

### Configuration Changes

```java
// ❌ BREAKING: Removed configuration property
// Before:
@Value("${app.legacy.setting}")
private String legacySetting;
// After:
// Property removed (breaks services using it!)

// ❌ BREAKING: Changed property name
// Before:
@Value("${app.timeout}")
private int timeout;
// After:
@Value("${app.request.timeout}")
private int timeout;

// ✅ SAFE: Added new property with default
@Value("${app.new.feature.enabled:false}")
private boolean newFeatureEnabled;

// ✅ SAFE: Deprecated old property, support both
@Value("${app.request.timeout:${app.timeout:5000}}")
private int timeout;
```

## Version Bump Checklist

Before bumping service-common version:

### For PATCH releases (bug fixes):
- [ ] No API changes
- [ ] No new features
- [ ] All changes are internal/fixes only
- [ ] Tests pass in service-common
- [ ] No new dependencies

### For MINOR releases (new features):
- [ ] All changes are backwards compatible
- [ ] New APIs are additive only
- [ ] Deprecated APIs still work (with warnings)
- [ ] Database migrations are additive (nullable columns, new tables)
- [ ] Tests pass in service-common
- [ ] Built and tested against transaction-service
- [ ] Built and tested against currency-service
- [ ] Documentation updated (Javadoc, CHANGELOG.md)
- [ ] Migration examples provided (if deprecating APIs)

### For MAJOR releases (breaking changes):
- [ ] Migration guide written
- [ ] All consuming services reviewed for impact
- [ ] Coordination plan for simultaneous upgrade
- [ ] Rollback plan documented
- [ ] All service teams notified
- [ ] Tests pass across all services with changes
- [ ] Documentation comprehensively updated

## Communication and Coordination

### When releasing new versions:

1. **Update CHANGELOG.md**: Document all changes, especially deprecations
2. **Notify service teams**: Announce releases in team channels
3. **Provide migration examples**: Show before/after code for breaking changes
4. **Update documentation**: Keep CLAUDE.md and docs/ in sync
5. **Test thoroughly**: Verify against all consuming services before release

### For deprecations:

1. **Give advance warning**: Announce deprecation in release notes
2. **Document alternatives**: Show what to use instead
3. **Set removal timeline**: Specify when deprecated API will be removed
4. **Track usage**: Help teams identify deprecated API usage in their code

## Tools and Automation

### Checking for breaking changes

```bash
# Use japi-compliance-checker or similar tools
# (Future enhancement: integrate into CI/CD)

# Manual checks:
# 1. Review all public API changes
git diff main -- src/main/java/**/[^T]*.java | grep "^-.*public"

# 2. Check for removed methods
# 3. Verify method signatures unchanged
# 4. Confirm exception types unchanged
```

### Testing against consuming services

```bash
# Script to test all services (future automation)
# For now: manual testing in each service repo

# transaction-service
cd ../transaction-service
./gradlew clean build

# currency-service
cd ../currency-service
./gradlew clean build
```

## Summary

**Golden Rule**: If you're unsure whether a change is breaking, assume it is and add new APIs instead.

**Remember**: The short-term convenience of modifying existing APIs is not worth the long-term pain of version fragmentation and coordination issues. Always maintain backwards compatibility to support our lockstep upgrade strategy.

# Versioning and Backwards Compatibility

## Core Principle

**CRITICAL**: ALL changes to **spring-platform**, **spring-cloud-platform**, **service-core**, and **service-web** MUST be backwards compatible. We maintain a common platform across all microservices, but `service-common` is a library version, not the global Budget Analyzer deployment version.

## Multi-Module Versioning

**spring-platform**, **spring-cloud-platform**, **service-core**, and **service-web** are versioned **together as a coordinated set**:
- All artifacts share the same version number (for example, `0.0.1-SNAPSHOT`
  for a local snapshot build)
- All artifacts are released together as one `service-common` library set
- Changes to any artifact trigger one service-common version bump
- Consuming services upgrade the platform and runtime library artifacts by
  changing their checked-in `serviceCommon` coordinate when they intentionally
  adopt that library release

## Why Backwards Compatibility Matters

### Independent Service Releases

Budget Analyzer services may publish and deploy runtime artifacts
independently. A service image release, frontend release, or orchestration
configuration change does not automatically imply a new
`service-common` release.

This makes backwards compatibility stricter, not looser:

- different services may consume different `service-common` versions during a
  rollout window
- a change to one service's runtime artifact should not force unrelated
  services to rebuild or change library coordinates
- runtime environments may contain mixed service artifact versions and
  `service-common` coordinates
- rollbacks are simpler when a service can return to its previous runtime
  artifact and library coordinate without requiring a repository-wide library
  version move

Coordinated `service-common` adoption across every Java consumer is still valid
for broad platform changes or intentional shared-library upgrades. It is a
library-consumer decision, not a default side effect of unrelated runtime or
configuration changes.

### Problems with Breaking Changes

Breaking changes undermine independent service deployment by:
- **Forcing coordinated upgrades**: Services must upgrade together instead of
  adopting the library when they actually need it
- **Increasing version risk**: Services on older library coordinates can break
  when compatibility is assumed but not preserved
- **Missed compatibility issues**: Integration problems surface later in a
  consuming service release rather than at the library change
- **Deployment coordination pressure**: Rollout and rollback become tied to a
  shared-library migration instead of a service artifact
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
- Removing platform constraints or imported BOMs that consumers rely on
- Moving to a major Spring Boot, Spring Cloud, or Spring Modulith version that requires consumer source or configuration changes

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
- Build and test against the Java services that consume the changed surface
- Prefer testing all Java consumers before publishing a shared-library release
- Run integration test suites for affected services
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

The service-common artifacts follow [Semantic Versioning 2.0.0](https://semver.org/):

**Important**: `spring-platform`, `spring-cloud-platform`, `service-core`, and `service-web` share the same version number and are always released together.

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

## Release Publishing

`service-common` uses the checked-in `version = "..."` literal in
`build.gradle.kts` as the release version source of truth.

Publishing `service-common` only makes a Maven coordinate available. Consuming
services adopt it by changing their checked-in `serviceCommon` version in
`gradle/libs.versions.toml` and rebuilding through their normal release path.
Do not assume a published library automatically changes any runtime service.

### Local Development Publish

```bash
./gradlew publishToMavenLocal
```

This is the supported local development path. It does not require GitHub
credentials and is what the side-by-side orchestration/Tilt workflow uses.

### Exceptional Manual GitHub Packages Validation

```bash
export GITHUB_ACTOR=<your-github-username>
export GITHUB_TOKEN=<token-with-packages-write-access>
./gradlew publish
```

This publishes all service-common artifacts to
`https://maven.pkg.github.com/budgetanalyzer/service-common` using the
checked-in version literal from `build.gradle.kts`.

Use this only for isolated validation. It is not the normal release path and it
is not a contributor prerequisite for consuming-service local development.

### Snapshot Workflow

The main-branch snapshot workflow is
`.github/workflows/publish-snapshot.yml`. It:

- Runs on pushes to `main` and on manual dispatch
- Reads the checked-in `version = "..."` literal in `build.gradle.kts`
- Publishes only when that version ends with `-SNAPSHOT`
- Skips cleanly when `main` is temporarily on a numeric release version for the
  release/tag sequence
- Uses `GITHUB_ACTOR=${{ github.actor }}` and the workflow `GITHUB_TOKEN`
- Writes the published version and GitHub Packages links to the workflow log
  and job summary after a successful publish

Normal snapshot usage is:

1. Keep `build.gradle.kts` on the active development version, for example
   `0.0.9-SNAPSHOT`.
2. Merge changes to `main`.
3. Let GitHub Actions refresh the published snapshot coordinate in GitHub
   Packages.

This lets consuming CI workflows resolve the current snapshot remotely while
local contributors still use `publishToMavenLocal` and orchestration/Tilt.

### Release Workflow

The tag-driven release workflow is
`.github/workflows/publish-release.yml`. It:

- Runs only for `v*` tags
- Strips the leading `v` from the tag
- Compares that value to the checked-in `version = "..."` literal in
  `build.gradle.kts`
- Fails fast on drift before running `./gradlew publish` with
  `GITHUB_ACTOR=${{ github.actor }}` and the workflow `GITHUB_TOKEN`
- Writes the published version and GitHub Packages links to the workflow log
  and job summary after a successful publish

Normal release usage is:

1. Merge the PR that sets `build.gradle.kts` to the release version on `main`
   (for example, `0.0.8`).
2. Create the matching tag from that merged `main` commit.
3. Push the tag so GitHub Actions publishes the release.

```bash
git fetch origin
git checkout main
git pull --ff-only origin main
git tag v<service-common-version>
git push origin v<service-common-version>
```

Create the tag before the next development-version PR moves `main` forward to
the next snapshot.

The snapshot workflow does not replace the release workflow. The intended
steady state is:

- `main` usually carries a `-SNAPSHOT` version and publishes snapshots
- release prep temporarily sets `main` to the numeric release version
- the matching `v*` tag publishes the release artifact
- `main` then moves back to the next `-SNAPSHOT`

Consuming services should still use the orchestration getting-started flow and
Maven Local for routine local work. GitHub Packages consumption is a
GitHub-Actions/release concern or an intentional isolated-build concern, not a
local contributor prerequisite. See
[orchestration/docs/development/service-common-artifact-resolution.md](../../orchestration/docs/development/service-common-artifact-resolution.md).

### Published Coordinates

All service-common artifacts are published together using the same checked-in version literal
from `build.gradle.kts`:

```text
org.budgetanalyzer:spring-platform:<service-common-version>
org.budgetanalyzer:spring-cloud-platform:<service-common-version>
org.budgetanalyzer:service-core:<service-common-version>
org.budgetanalyzer:service-web:<service-common-version>
```

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
4. **Update documentation**: Keep AGENTS.md and docs/ in sync
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

**Remember**: The short-term convenience of modifying existing APIs is not worth the long-term pain of forced coordinated service releases. Always maintain backwards compatibility so services can adopt `service-common` releases intentionally.

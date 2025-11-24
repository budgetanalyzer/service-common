# Code Quality Standards - Budget Analyzer Microservices

## Overview

All Budget Analyzer microservices follow strict code quality standards enforced through automated tooling and code review.

## Why Open-Source Tooling

**Core Principle**: All development tooling is open-source with no proprietary dependencies.

### Rationale

1. **AI-Assisted Development**: Claude files (CLAUDE.md) can be shared across public repositories without licensing or SSL issues. This enables consistent AI-assisted development workflows across the ecosystem.

2. **Single Core Dev Environment**: We standardize on one tooling stack that works everywhere - local development, CI/CD, and AI agents. No "works on my machine" issues from proprietary formatters or analyzers.

3. **Community Standards**: Google Java Format and Checkstyle are industry-standard, well-documented, and actively maintained. New developers and AI assistants understand them immediately.

4. **Future-Proof**: Open-source tools can be audited, forked, and extended. We're not dependent on any vendor's roadmap or licensing changes.

This is the foundation for collaborative AI-native development - a standardized, reproducible environment that humans and AI can share.

## Spotless Configuration

**Purpose**: Automatic code formatting with Google Java Format

**Scope**: Applied to all modules (service-core, service-web, and all consuming services)

**Features**:
- Google Java Format 1.17.0
- Automatic import ordering: `java` → `javax` → `jakarta` → `org` → `com` → `org.budgetanalyzer`
- Trailing whitespace removal
- Files end with newline
- Unused import removal

**Usage**:
```bash
# Format code in all modules
./gradlew clean spotlessApply

# Check formatting without applying (all modules)
./gradlew spotlessCheck

# Format specific module only
./gradlew :service-core:spotlessApply
./gradlew :service-web:spotlessApply
```

## Checkstyle Enforcement

**Purpose**: Static code analysis for style and best practices

**Scope**: Applied to all modules (service-core, service-web)

**Configuration**:
- Version 12.0.1
- Custom rules in `config/checkstyle/checkstyle.xml` (shared across all modules)
- Enforces Hibernate import ban
- Enforces naming conventions
- Validates Javadoc completeness

**Common Issues**:
- Javadoc missing periods at end of first sentence
- Missing Javadoc on public methods/classes
- Wildcard imports (`import java.util.*`)
- Hibernate-specific imports (`import org.hibernate.*`)
- Line length violations (120 characters)
- Naming convention violations

## Build Commands

**CRITICAL**: Always use these two commands in sequence:

```bash
# 1. Format code (always run first - formats all modules)
./gradlew clean spotlessApply

# 2. Build and test (always run second - builds all modules)
./gradlew clean build
```

The `clean build` command will:
- Clean previous build artifacts in all modules
- Compile all source code (service-core and service-web)
- Run all tests in all modules
- Execute Checkstyle validation on all modules
- Run Spotless checks
- Run Checkstyle
- Run all unit and integration tests
- Build the JAR file

**Never use** individual gradle tasks like `check`, `bootJar`, `checkstyleMain`, etc. Always use the full `clean build` sequence.

## Variable Declarations and Naming (Unified Design)

**Core Principle**: `var` usage and type-based naming work together as a unified design. The variable name IS the type documentation - no mental mapping required.

### Why This Works

When you write:
```java
var currencySeriesRepository = getCurrencySeriesRepository();
```

The variable name `currencySeriesRepository` tells you exactly what type it is. Compare to:
```java
CurrencySeriesRepository repository = getCurrencySeriesRepository();
```

Here you must mentally track that `repository` means `CurrencySeriesRepository` throughout the method. With our convention, there's no disconnect - the name IS the type.

### The Two Rules

**Rule 1**: Use `var` whenever possible for local variables
**Rule 2**: Name variables by their full type in camelCase

These rules are inseparable - using `var` without type-based naming loses type information; using type-based naming without `var` creates redundancy.

### Examples
```java
// ✅ CORRECT - Self-documenting, no redundancy
var currencySeriesRepository = getCurrencySeriesRepository();
var transactionService = new TransactionService();
var exchangeRates = provider.getExchangeRates();

// ❌ WRONG - Lost type information
var repo = getCurrencySeriesRepository();
var service = new TransactionService();

// ❌ WRONG - Redundant
CurrencySeriesRepository currencySeriesRepository = getCurrencySeriesRepository();
```

### When NOT to Use `var`
```java
// Use explicit type when type isn't inferrable
Map<String, Object> details = Map.of("method", "POST", "uri", "/api/users", "status", 201);
```

## Variable Naming Details

**Rule**: Variable names should use the full class name in camelCase to improve code readability and IDE autocomplete discoverability.

### Class-Level Fields (STRICTLY ENFORCED)

Class-level fields (instance variables and static fields) **MUST** use the full class name.

```java
// ✅ CORRECT - Full class name used
private CurrencySeriesRepository currencySeriesRepository;
private TransactionService transactionService;
private ObjectMapper objectMapper;
private static final Logger logger = LoggerFactory.getLogger(MyClass.class);

// ❌ WRONG - Shortened/generic names
private CurrencySeriesRepository repository;  // Too generic
private TransactionService service;           // Too generic
private ObjectMapper mapper;                  // Too short
```

### Method-Level Variables (RECOMMENDED)

Local variables within methods **SHOULD** follow the same convention, but this is recommended rather than strictly enforced.

```java
// ✅ PREFERRED - Full class name
public void processTransaction() {
    var transactionService = new TransactionService();
    var currencyConverter = new CurrencyConverter();
    transactionService.save(transaction);
}

// ⚠️ ACCEPTABLE - Shorter names when context is clear
public void process() {
    var service = getService();  // Acceptable in small methods
    var converter = new CurrencyConverter();
}
```

### Common Exceptions

The following abbreviations are acceptable when they are standard Java conventions:

```java
// ✅ Standard abbreviations are acceptable
private static final Logger log = LoggerFactory.getLogger(MyClass.class);
var sb = new StringBuilder();
var ex = new RuntimeException();  // In catch blocks

// ✅ Collections - plural form is acceptable
List<Currency> currencies;        // Not currencyList
Set<Transaction> transactions;    // Not transactionSet
```

### Rationale

- **Discoverability**: Full names make IDE autocomplete more effective
- **Clarity**: Reduces ambiguity about what a variable represents
- **Consistency**: Establishes a predictable naming pattern across the codebase
- **Refactoring**: Makes it easier to search and refactor code

## Import Rules

**Rule**: No wildcard imports - always use explicit imports

```java
// ❌ WRONG - Wildcard import
import java.util.*;
import org.budgetanalyzer.service.*;

// ✅ CORRECT - Explicit imports
import java.util.List;
import java.util.ArrayList;
import org.budgetanalyzer.service.TransactionService;
import org.budgetanalyzer.service.CurrencyService;
```

**Import Order** (enforced by Spotless):
1. `java.*`
2. `javax.*`
3. `jakarta.*`
4. `org.*`
5. `com.*`
6. `org.budgetanalyzer.*`

## Method Formatting

### Return Statement Spacing

**Rules**:
- **3+ lines of logic**: Add blank line before `return`
- **1-2 line combo**: NO blank line before `return`
- **Single-line methods**: No blank line
- **Guard clauses**: No blank line before early `return`
- **Direct returns**: Don't create unnecessary variables

### Examples

**✅ CORRECT: Direct return (no intermediate variable)**
```java
public String toJson(Object object) {
    return objectMapper.writeValueAsString(object);
}
```

**✅ CORRECT: 2-line combo (NO blank line)**
```java
public TransactionResponse getById(Long id) {
    var transaction = transactionService.getById(id);
    return TransactionResponse.from(transaction);
}
```

**✅ CORRECT: Multi-step logic (3+ lines with blank line)**
```java
public Transaction createTransaction(TransactionRequest request) {
    var entity = request.toEntity();
    validateTransaction(entity);

    return transactionRepository.save(entity);
}
```

**✅ CORRECT: Simple getter**
```java
public String getBankName() {
    return bankName;
}
```

**✅ CORRECT: Guard clause (no blank line)**
```java
public void validate(Transaction transaction) {
    if (transaction.getDate() == null) {
        throw new IllegalArgumentException("Date required");
    }

    // ... more logic
}
```

**❌ WRONG: Unnecessary variable**
```java
public String toJson(Object object) {
    var json = objectMapper.writeValueAsString(object);
    return json;  // Variable adds no value
}
```

**❌ WRONG: Blank line in 2-line combo**
```java
public TransactionResponse getById(Long id) {
    var transaction = transactionService.getById(id);

    return TransactionResponse.from(transaction);
}
```

**❌ WRONG: Missing blank line (3+ lines)**
```java
public Transaction toEntity() {
    var entity = new Transaction();
    entity.setBankName(bankName);
    entity.setDate(date);
    return entity;  // Needs blank line before this
}
```

## Javadoc Comments

**CRITICAL**: First sentence MUST end with a period (`.`) - enforced by Checkstyle `SummaryJavadoc` rule

### Why This Matters
- First sentence appears in method/class listings
- Used by IDEs for quick documentation
- Javadoc tools extract first sentence as summary

### Format Rules
- **Single-line**: `/** Summary sentence here. */`
- **Multi-line**: First line after `/**` must end with period
- **Fields**: Even short descriptions need periods
- **Always**: End the summary sentence with a period

### Examples

**✅ CORRECT: Single-line with period**
```java
/** Converts object to JSON string with sensitive fields masked. */
public static String toJson(Object object) { }

/** Header name for correlation ID. */
public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
```

**✅ CORRECT: Multi-line with period**
```java
/**
 * Masks a sensitive string value.
 *
 * @param value The value to mask
 * @param showLast Number of characters to show at the end
 * @return Masked value
 */
public static String mask(String value, int showLast) { }
```

**❌ WRONG: Missing period**
```java
/** Converts object to JSON string with sensitive fields masked */
public static String toJson(Object object) { }

/** Header name for correlation ID */
public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
```

## Checkstyle Warning Handling

**CRITICAL**: When running `./gradlew clean build`, **always** pay attention to Checkstyle warnings.

### Required Actions

1. **Read build output carefully** - Look for Checkstyle warnings even if build succeeds
2. **Treat warnings as errors** - Fix all warnings immediately
3. **Never ignore warnings** - Even passing builds with warnings indicate quality issues

### Common Warnings

| Warning | Cause | Fix |
|---------|-------|-----|
| `SummaryJavadoc` | Javadoc first sentence missing period | Add `.` at end |
| `JavadocMethod` | Missing Javadoc on public method | Add `/** ... */` comment |
| `AvoidStarImport` | Wildcard import used | Expand to explicit imports |
| `IllegalImport` | Hibernate import detected | Use `jakarta.persistence.*` |
| `LineLength` | Line exceeds 120 characters | Break into multiple lines |
| `NeedBraces` | Missing braces on if/for/while | Add `{ }` braces |

### Response Pattern

When encountering warnings, follow this pattern:

**If fixable:**
```
Build completed successfully, but found Checkstyle warnings:
- File: src/main/java/org/budgetanalyzer/service/Example.java:42
- Issue: Javadoc comment missing period at end of first sentence
- Action: Fixed by adding period to Javadoc summary

Rerunning build to verify fix...
```

**If not fixable:**
```
Build completed with Checkstyle warnings that I cannot resolve:
- File: src/main/java/org/budgetanalyzer/service/Example.java:42
- Warning: [specific warning message]
- Reason: [explanation of why it cannot be fixed]

Please advise on how to proceed.
```

## Code Review Checklist

Before committing code, verify:

- [ ] `./gradlew clean spotlessApply` has been run
- [ ] `./gradlew clean build` passes without errors
- [ ] No Checkstyle warnings in build output
- [ ] All public methods have Javadoc
- [ ] Javadoc first sentences end with periods
- [ ] No wildcard imports
- [ ] No Hibernate-specific imports
- [ ] `var` used where appropriate
- [ ] Return statement spacing follows rules
- [ ] All tests pass

## IDE Configuration

> **Note:** IntelliJ IDEA is not supported. It cannot run containerized AI agents, making it unsuitable for AI-assisted development workflows.

### VS Code

**Java Extension Pack**:
1. Install "Extension Pack for Java"
2. Install "Checkstyle for Java"
3. Configure formatting:
   ```json
   {
     "java.format.settings.url": "config/eclipse-java-google-style.xml",
     "java.checkstyle.configuration": "config/checkstyle/checkstyle.xml"
   }
   ```

## Automated Checks in CI/CD

**GitHub Actions** (or Jenkins):
```yaml
- name: Check code formatting
  run: ./gradlew spotlessCheck

- name: Run Checkstyle
  run: ./gradlew checkstyleMain checkstyleTest

- name: Build and test
  run: ./gradlew clean build
```

Failures in any of these steps should block merge/deployment.

## Troubleshooting

### Spotless Failures
```bash
# Fix formatting violations
./gradlew clean spotlessApply
```

### Checkstyle Failures
```bash
# View detailed Checkstyle report
./gradlew checkstyleMain
cat build/reports/checkstyle/main.html
```

### Build Failures
```bash
# Clean and rebuild from scratch
./gradlew clean spotlessApply
./gradlew clean build
```

## Resources

- [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
- [Checkstyle Documentation](https://checkstyle.org/)
- [Spotless Plugin](https://github.com/diffplug/spotless)
- Project-specific rules: `config/checkstyle/checkstyle.xml`

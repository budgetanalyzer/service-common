# Service Common

> **⚠️ Work in Progress**: This project is under active development. Features and documentation are subject to change.

Shared core library for Budget Analyzer microservices - a personal finance management application.

## Overview

This module provides common functionality shared across all Budget Analyzer backend microservices, including:

- Base entity classes and DTOs
- Common utilities and helpers
- Shared validation logic
- OpenAPI/Swagger integration
- CSV parsing capabilities
- Standard Spring Boot configurations

## Purpose

Service Common promotes code reuse and consistency across microservices by:
- Reducing code duplication
- Ensuring consistent data models
- Providing shared utilities
- Standardizing error handling and validation

## Technology Stack

- **Java 24**
- **Spring Boot 3.x** (Starter Web, Data JPA)
- **SpringDoc OpenAPI** for API documentation
- **OpenCSV** for CSV file processing
- **JUnit 5** for testing

## Usage

This library is published to Maven Local and consumed by other Budget Analyzer services.

### Adding as a Dependency

In your service's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("org.budgetanalyzer:service-common:0.0.1-SNAPSHOT")
}

repositories {
    mavenLocal()
    mavenCentral()
}
```

### Building and Publishing

```bash
# Build the library
./gradlew build

# Publish to Maven Local
./gradlew publishToMavenLocal
```

## Development

### Prerequisites

- JDK 24
- Gradle (wrapper included)

### Building

```bash
# Clean and build
./gradlew clean build

# Run tests
./gradlew test

# Check code style
./gradlew spotlessCheck

# Apply code formatting
./gradlew spotlessApply
```

## Code Quality

This project enforces code quality through:
- **Google Java Format** for consistent code style
- **Checkstyle** for code standards
- **Spotless** for automated formatting

## Project Structure

```
service-common/
├── src/
│   ├── main/java/         # Shared Java code
│   └── test/java/         # Unit tests
├── build.gradle.kts       # Build configuration
└── config/                # Checkstyle configuration
```

## Related Repositories

- **Orchestration**: https://github.com/budget-analyzer/orchestration
- **Transaction Service**: https://github.com/budget-analyzer/transaction-service
- **Currency Service**: https://github.com/budget-analyzer/currency-service
- **Web Frontend**: https://github.com/budget-analyzer/budget-analyzer-web

## License

MIT

## Contributing

This project is currently in early development. Contributions, issues, and feature requests are welcome as we build toward a stable release.

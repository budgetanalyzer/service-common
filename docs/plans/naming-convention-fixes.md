# Naming Convention Fixes

Apply our unified `var` + type-named variables standard to this repository.

## Issues Found

### Production Code (MUST FIX)

| File | Line | Current | Should Be |
|------|------|---------|-----------|
| `service-web/.../HttpLoggingFilter.java` | 45 | `properties` | `httpLoggingProperties` |
| `service-web/.../ReactiveHttpLoggingFilter.java` | 48 | `properties` | `httpLoggingProperties` |
| `service-core/.../SafeLogger.java` | 19 | `var mapper` | `var objectMapper` |

### Test Code (SHOULD FIX)

| File | Line | Current | Should Be |
|------|------|---------|-----------|
| `BaseOpenApiConfigTest.java` | 26 | `config` | `testOpenApiConfig` |
| `BaseOpenApiConfigTest.java` | 45 | `var props` | `var springDocConfigProperties` |
| `HttpLoggingFilterTest.java` | 30 | `properties` | `httpLoggingProperties` |
| `ContentLoggingUtilTest.java` | 39 | `properties` | `httpLoggingProperties` |
| `ReactiveHttpLoggingFilterTest.java` | 33 | `properties` | `httpLoggingProperties` |
| `OpenCsvParserTest.java` | 23 | `parser` | `openCsvParser` |
| `TestSecurityConfigTest.java` | 23, 38, 50, 84 | `var config` | `var testSecurityConfig` |
| `ComponentScanningIntegrationTest.java` | 62 | `var filter` | `var correlationIdFilter` |
| `ComponentScanningIntegrationTest.java` | 69 | `var filter` | `var httpLoggingFilter` |

## Additional Issue

### Deprecation Warning

```
service-web/src/main/java/.../CachedBodyServerHttpRequestDecorator.java
uses or overrides a deprecated API.
```

Needs investigation - run with `-Xlint:deprecation` for details.

## Verification

After fixes, run:
```bash
./gradlew clean spotlessApply
./gradlew clean build
```

All tests should pass with no warnings.

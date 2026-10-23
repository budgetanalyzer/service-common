description = "Spring Boot web service components with auto-configuration"

dependencies {
    api(platform(project(":spring-platform")))

    // Core module dependency (transitive to consumers via api())
    api(project(":service-core"))

    // Common dependencies - transitive
    api(libs.commons.lang3)
    api(libs.spring.boot.starter.validation)

    // Stack-specific - compile-only (NOT transitive)
    // Services must explicitly add the stack they need
    compileOnly(libs.spring.boot.starter.webmvc)
    compileOnly(libs.spring.boot.starter.webflux)
    compileOnly(libs.spring.boot.starter.data.jpa)
    compileOnly(libs.spring.boot.starter.security)

    // SpringDoc - compile-only (services choose servlet or reactive version)
    compileOnly(libs.springdoc.openapi)

    // Test support utilities (published for consuming services' tests)
    // ClaimsHeaderTestBuilder implements RequestPostProcessor from spring-test
    compileOnly(libs.spring.security.test)

    // Test - need both stacks for testing
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.boot.starter.webflux.test) // Includes reactor-test
    testImplementation(libs.spring.boot.starter.data.jpa.test)
    testImplementation(libs.spring.boot.starter.security.test)
    testImplementation(libs.springdoc.openapi)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.h2)
}

// Isolated consumer-classpath smoke suites. Each suite depends on this project's api and runtime
// variants (the same variants the published module metadata is generated from) plus a single web
// stack, and does not inherit the combined servlet + reactive + JPA test classpath above. The
// suites do not exercise the generated POM or module files.
testing {
    suites {
        register<JvmTestSuite>("servletSmokeTest") {
            dependencies {
                implementation(project())
                implementation(libs.spring.boot.starter.webmvc)
                implementation(libs.spring.boot.starter.security)
                implementation(libs.springdoc.openapi)
                implementation(libs.spring.boot.starter.webmvc.test)
                implementation(libs.spring.boot.starter.security.test)
                runtimeOnly(libs.junit.platform.launcher)
            }
        }

        register<JvmTestSuite>("reactiveSmokeTest") {
            dependencies {
                implementation(project())
                implementation(libs.spring.boot.starter.webflux)
                implementation(libs.spring.boot.starter.security)
                implementation(libs.springdoc.openapi.webflux)
                implementation(libs.spring.boot.starter.webflux.test)
                implementation(libs.spring.boot.starter.security.test)
                runtimeOnly(libs.junit.platform.launcher)
            }
        }
    }
}

tasks.named("check") {
    dependsOn(testing.suites.named("servletSmokeTest"), testing.suites.named("reactiveSmokeTest"))
}

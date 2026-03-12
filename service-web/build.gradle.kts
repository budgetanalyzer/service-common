plugins {
    alias(libs.plugins.spring.dependency.management)
}

description = "Spring Boot web service components with auto-configuration"

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.versions.springBoot.get()}")
    }
}

dependencies {
    // Core module dependency (transitive to consumers via api())
    api(project(":service-core"))

    // Common dependencies - transitive
    api(libs.commons.lang3)
    api(libs.spring.boot.starter.validation)

    // Stack-specific - compile-only (NOT transitive)
    // Services must explicitly add the stack they need
    compileOnly(libs.spring.boot.starter.web)
    compileOnly(libs.spring.boot.starter.webflux)
    compileOnly(libs.spring.boot.starter.data.jpa)
    compileOnly(libs.spring.boot.starter.security)

    // SpringDoc - compile-only (services choose servlet or reactive version)
    compileOnly(libs.springdoc.openapi)

    // Test support utilities (published for consuming services' tests)
    // ClaimsHeaderTestBuilder implements RequestPostProcessor from spring-test
    compileOnly(libs.spring.security.test)

    // Test - need both stacks for testing
    testImplementation(libs.spring.boot.starter.web)
    testImplementation(libs.spring.boot.starter.webflux)
    testImplementation(libs.spring.boot.starter.data.jpa)
    testImplementation(libs.spring.boot.starter.security)
    testImplementation(libs.springdoc.openapi)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.security.test)
    testImplementation(libs.reactor.test) // For reactive testing
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.h2)
}

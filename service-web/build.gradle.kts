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
    compileOnly(libs.spring.boot.starter.oauth2.resource.server)

    // SpringDoc - compile-only (services choose servlet or reactive version)
    compileOnly(libs.springdoc.openapi)

    // Test support (published for consuming services' tests)
    api(libs.mockito.core) // Used by security.test package

    // Test - need both stacks for testing
    testImplementation(libs.spring.boot.starter.web)
    testImplementation(libs.spring.boot.starter.webflux)
    testImplementation(libs.spring.boot.starter.data.jpa)
    testImplementation(libs.spring.boot.starter.oauth2.resource.server)
    testImplementation(libs.springdoc.openapi)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.security.test)
    testImplementation("io.projectreactor:reactor-test") // For reactive testing
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.h2)
}

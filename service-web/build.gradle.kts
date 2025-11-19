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

    // Spring Boot (use api() for implicit dependencies)
    api(libs.spring.boot.starter.web)
    api(libs.spring.boot.starter.data.jpa)
    api(libs.spring.boot.starter.oauth2.resource.server)
    // Note: actuator comes from service-core (available to all services)

    // Third-party
    api(libs.springdoc.openapi)
    implementation(libs.commons.lang3)

    // Test support (published for consuming services' tests)
    api("org.mockito:mockito-core") // Used by security.test package

    // Test
    testImplementation(libs.spring.boot.starter.test)
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.h2)
}

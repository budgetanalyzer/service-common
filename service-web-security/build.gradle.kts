plugins {
    alias(libs.plugins.spring.dependency.management)
}

description = "OAuth2 Resource Server security components with auto-configuration"

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.versions.springBoot.get()}")
    }
}

dependencies {
    // Depend on service-web for exception handling and API error responses
    api(project(":service-web"))

    // OAuth2 Resource Server dependencies
    api(libs.spring.boot.starter.oauth2.resource.server)

    // Test dependencies
    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

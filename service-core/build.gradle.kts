description = "Core utilities for microservices - minimal dependencies"

dependencies {
    api(platform(project(":spring-platform")))

    // TRANSITIVE (api) - every service gets these
    api(libs.spring.boot.starter.actuator)
    api(libs.micrometer.registry.prometheus)

    // NOT TRANSITIVE (implementation) - services opt-in
    implementation(libs.jackson.databind)
    implementation(libs.slf4j.api)

    // NOT TRANSITIVE (compileOnly) - services using JPA entities must declare explicitly
    compileOnly(libs.spring.boot.starter.data.jpa)
    compileOnly(libs.spring.security.core)

    implementation(libs.opencsv)

    // Test dependencies
    testImplementation(libs.spring.boot.starter.data.jpa.test)
    testImplementation(libs.spring.boot.starter.security.test)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.h2)
}

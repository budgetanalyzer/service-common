description = "Budget Analyzer Spring dependency platform"

javaPlatform {
    allowDependencies()
}

dependencies {
    api(platform("org.springframework.boot:spring-boot-dependencies:${libs.versions.springBoot.get()}"))
    api(platform("org.springframework.modulith:spring-modulith-bom:${libs.versions.springModulith.get()}"))

    constraints {
        api("org.springdoc:springdoc-openapi-starter-webmvc-ui:${libs.versions.springdoc.get()}")
        api("org.springdoc:springdoc-openapi-starter-webflux-ui:${libs.versions.springdoc.get()}")
        api("org.testcontainers:testcontainers:${libs.versions.testcontainers.get()}") {
            because("Keep Testcontainers pinned at the selected 1.21.x patch level for Docker 29.x compatibility")
        }
        api("org.testcontainers:junit-jupiter:${libs.versions.testcontainers.get()}") {
            because("Keep Testcontainers pinned at the selected 1.21.x patch level for Docker 29.x compatibility")
        }
        api("org.testcontainers:postgresql:${libs.versions.testcontainers.get()}") {
            because("Keep Testcontainers pinned at the selected 1.21.x patch level for Docker 29.x compatibility")
        }
        api("org.testcontainers:rabbitmq:${libs.versions.testcontainers.get()}") {
            because("Keep Testcontainers pinned at the selected 1.21.x patch level for Docker 29.x compatibility")
        }
        api("org.wiremock:wiremock-standalone:${libs.versions.wiremock.get()}")
        api("org.awaitility:awaitility:${libs.versions.awaitility.get()}")
        api("net.javacrumbs.shedlock:shedlock-spring:${libs.versions.shedlock.get()}")
        api("net.javacrumbs.shedlock:shedlock-provider-jdbc-template:${libs.versions.shedlock.get()}")
    }
}

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
        api("org.wiremock:wiremock-standalone:${libs.versions.wiremock.get()}")
        api("org.awaitility:awaitility:${libs.versions.awaitility.get()}")
        api("net.javacrumbs.shedlock:shedlock-spring:${libs.versions.shedlock.get()}")
        api("net.javacrumbs.shedlock:shedlock-provider-jdbc-template:${libs.versions.shedlock.get()}")
    }
}

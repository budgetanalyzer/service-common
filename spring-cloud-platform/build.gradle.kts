description = "Budget Analyzer Spring Cloud dependency platform"

javaPlatform {
    allowDependencies()
}

dependencies {
    api(platform(project(":spring-platform")))
    api(platform("org.springframework.cloud:spring-cloud-dependencies:${libs.versions.springCloud.get()}"))
}

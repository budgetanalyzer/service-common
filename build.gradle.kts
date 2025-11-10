plugins {
    java
    checkstyle
    id("org.springframework.boot") version "3.5.6" apply false
    id("io.spring.dependency-management") version "1.1.7"
    id("com.diffplug.spotless") version "8.0.0"
    id("maven-publish")
}

group = "org.budgetanalyzer"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(24)
    }
    withSourcesJar()
    withJavadocJar()
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.5.6")
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    implementation("com.opencsv:opencsv:3.7")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.13")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // the default jupiter packaged in the 3.5.6 starter is 5.10.0; overriding to 5.12.2
    // revisit when we upgrade spring boot version
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.2")
    testRuntimeOnly("com.h2database:h2")
}

spotless {
    java {
        googleJavaFormat("1.17.0")
        trimTrailingWhitespace()
        endWithNewline()
        importOrder("java", "javax", "jakarta", "org", "com", "", "org.budgetanalyzer")
        removeUnusedImports()
    }
}

checkstyle {
    toolVersion = "12.0.1"
}

tasks.named("check") {
    dependsOn("spotlessCheck")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<Javadoc> {
    options {
        (this as StandardJavadocDocletOptions).apply {
            addStringOption("Xdoclint:none", "-quiet")
            // External API links for generating clickable Javadoc references
            // Update these URLs when upgrading Spring Boot or Jakarta EE versions
            links(
                "https://docs.oracle.com/en/java/javase/24/docs/api/",
                "https://docs.spring.io/spring-framework/docs/6.2.2/javadoc-api/",
                "https://jakarta.ee/specifications/platform/10/apidocs/"
            )
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            groupId = project.group.toString()
            artifactId = "service-common"
            version = project.version.toString()

            pom {
                name.set("Service Common")
                description.set("Shared core module for microservices")

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
            }
        }
    }

    repositories {
        mavenLocal()
    }
}

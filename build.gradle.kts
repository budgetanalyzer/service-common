import com.diffplug.gradle.spotless.SpotlessExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    id("com.diffplug.spotless") version "8.0.0" apply false
}

allprojects {
    group = "org.budgetanalyzer"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "checkstyle")
    apply(plugin = "maven-publish")
    apply(plugin = "com.diffplug.spotless")

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(24))
        }
        withSourcesJar()
        withJavadocJar()
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging {
            events = setOf(TestLogEvent.FAILED, TestLogEvent.PASSED, TestLogEvent.SKIPPED)
            exceptionFormat = TestExceptionFormat.FULL
            showExceptions = true
            showCauses = true
            showStackTraces = true
        }
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

    // Shared spotless configuration
    configure<SpotlessExtension> {
        java {
            googleJavaFormat("1.32.0")
            trimTrailingWhitespace()
            endWithNewline()
            importOrder("java", "javax", "jakarta", "org", "com", "", "org.budgetanalyzer")
            removeUnusedImports()
        }
    }

    // Shared checkstyle configuration
    configure<CheckstyleExtension> {
        toolVersion = "12.0.1"
        config = resources.text.fromUri("https://raw.githubusercontent.com/budgetanalyzer/checkstyle-config/main/checkstyle.xml")
    }

    tasks.named("check") {
        dependsOn("spotlessCheck")
    }

    // Publishing configuration
    configure<PublishingExtension> {
        publications {
            create<MavenPublication>("mavenJava") {
                from(components["java"])
                groupId = project.group.toString()
                artifactId = project.name
                version = project.version.toString()

                // Publish resolved versions for dependencies
                versionMapping {
                    allVariants {
                        fromResolutionResult()
                    }
                }

                pom {
                    name.set(project.name)
                    description.set(project.description ?: "Shared module for microservices")

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
}

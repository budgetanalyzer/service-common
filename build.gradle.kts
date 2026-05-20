import org.gradle.api.GradleException
import com.diffplug.gradle.spotless.SpotlessExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    id("com.diffplug.spotless") version "8.0.0" apply false
}

val githubPackagesActor = providers.environmentVariable("GITHUB_ACTOR")
val githubPackagesToken = providers.environmentVariable("GITHUB_TOKEN")
val platformProjectNames = setOf("spring-platform", "spring-cloud-platform")
val jacocoToolVersion = libs.versions.jacoco.get()
val coverageMinimumsByProject = mapOf(
    "service-core" to ("0.80".toBigDecimal() to "0.70".toBigDecimal()),
    "service-web" to ("0.93".toBigDecimal() to "0.80".toBigDecimal())
)

allprojects {
    group = "org.budgetanalyzer"
    version = "0.0.14"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "maven-publish")

    // Publishing configuration
    configure<PublishingExtension> {
        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/budgetanalyzer/service-common")
                credentials {
                    username = githubPackagesActor.orNull ?: ""
                    password = githubPackagesToken.orNull ?: ""
                }
            }
        }
    }

    tasks.withType<PublishToMavenRepository>().configureEach {
        doFirst {
            if (repository.name != "GitHubPackages") {
                return@doFirst
            }

            val missingEnvVars = listOfNotNull(
                "GITHUB_ACTOR".takeUnless { githubPackagesActor.isPresent },
                "GITHUB_TOKEN".takeUnless { githubPackagesToken.isPresent }
            )

            if (missingEnvVars.isNotEmpty()) {
                throw GradleException(
                    "Publishing to GitHub Packages requires ${missingEnvVars.joinToString(" and ")}."
                )
            }
        }
    }
}

configure(subprojects.filter { it.name !in platformProjectNames }) {
    apply(plugin = "java-library")
    apply(plugin = "checkstyle")
    apply(plugin = "com.diffplug.spotless")
    apply(plugin = "jacoco")

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
        withSourcesJar()
        withJavadocJar()
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        finalizedBy(tasks.named("jacocoTestReport"))
        testLogging {
            events = setOf(TestLogEvent.FAILED, TestLogEvent.PASSED, TestLogEvent.SKIPPED)
            exceptionFormat = TestExceptionFormat.FULL
            showExceptions = true
            showCauses = true
            showStackTraces = true
        }
    }

    configure<JacocoPluginExtension> {
        toolVersion = jacocoToolVersion
    }

    tasks.named<JacocoReport>("jacocoTestReport") {
        dependsOn(tasks.named("test"))
        reports {
            xml.required.set(true)
            html.required.set(true)
            csv.required.set(false)
        }
    }

    tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
        dependsOn(tasks.named("test"))

        val coverageMinimums = coverageMinimumsByProject.getValue(project.name)
        violationRules {
            rule {
                limit {
                    counter = "LINE"
                    value = "COVEREDRATIO"
                    minimum = coverageMinimums.first
                }
                limit {
                    counter = "BRANCH"
                    value = "COVEREDRATIO"
                    minimum = coverageMinimums.second
                }
            }
        }
    }

    tasks.withType<Javadoc> {
        options {
            (this as StandardJavadocDocletOptions).apply {
                addStringOption("Xdoclint:all,-missing", "-quiet")
                // External API links for generating clickable Javadoc references
                // Update these URLs when upgrading Spring Boot or Jakarta EE versions
                links(
                    "https://docs.oracle.com/en/java/javase/25/docs/api/",
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
        dependsOn("spotlessCheck", "jacocoTestCoverageVerification")
    }

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
                    description.set(project.provider { project.description ?: "Shared module for microservices" })

                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                }
            }
        }
    }
}

configure(subprojects.filter { it.name in platformProjectNames }) {
    apply(plugin = "java-platform")

    configure<PublishingExtension> {
        publications {
            create<MavenPublication>("mavenJava") {
                from(components["javaPlatform"])
                groupId = project.group.toString()
                artifactId = project.name
                version = project.version.toString()

                pom {
                    name.set(project.name)
                    description.set(project.provider { project.description ?: "Dependency platform for microservices" })

                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                }
            }
        }
    }
}

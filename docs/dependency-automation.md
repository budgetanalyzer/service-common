# Dependency Automation

The workspace-wide operating policy, activation steps, cost boundary, and
failure triage are owned by
[orchestration's dependency automation guide](../../orchestration/docs/dependency-automation.md).
This document records only the `service-common` integration and review checks.

## Update discovery

`renovate.json` extends the shared Budget Analyzer preset. Renovate's native
Gradle, Gradle Wrapper, and GitHub Actions managers discover this repository's
version catalog, build scripts, wrapper distribution, and workflow actions.
There are currently no Dockerfiles in this repository. Add file-specific
manager configuration only if a future dependency declaration is not covered
by a native manager.

The Phase 3 local extraction found 52 dependency occurrences across 12 files:
33 Gradle occurrences in seven files, one wrapper occurrence, and 18 GitHub
Actions occurrences in four workflows. Maven and Gradle lookups produced 41
candidate branches before scheduling, concurrency limits, and dashboard
approval. Maintained-line and later-major proposals were both visible for the
Spring platform, documentation, test, locking, and wrapper dependencies. The
Actions references were extracted, but authenticated GitHub lookups remain a
post-publication hosted check because no GitHub token is supplied to local
validation.

Renovate proposes changes only to direct declarations. It does not turn every
version inherited from the Spring Boot, Spring Cloud, or Spring Modulith BOMs
into an independently updateable declaration. The resolved dependency graph
described below is the security inventory for those inherited versions.

Major updates remain visible in the Dependency Dashboard and require approval.
In particular, Spring Boot 4 and the related Spring Cloud, Spring Framework,
Spring Security, Spring Modulith, SpringDoc, Testcontainers, ShedLock, and test
platform lines are a compatibility migration, not a routine patch. Renovate
cannot select the correct release-train combination or coordinate consumer
services by itself.

## Resolved dependency graph

`.github/workflows/dependency-submission.yml` runs on trusted pushes to `main`,
weekly, and by manual dispatch. The job runs only for the `main` ref and uses
the official `gradle/actions/dependency-submission` action with the open-source
`basic` cache provider. Its job token receives only `contents: write`, which is
required by GitHub's Dependency Submission API. The graph is submitted
directly and is not retained as a workflow artifact or published as a Build
Scan.

The action's default resolution task visits all projects and all resolvable
configurations. Do not add project or configuration filters without proving
equivalent coverage: the platform modules contribute imported BOMs, while the
library modules contribute compile, runtime, and test dependency trees.

Local Phase 3 validation generated an official plugin snapshot without
submitting it. The snapshot contained 216 resolved coordinate/version entries
across the four subprojects. It included the Spring Boot, Spring Cloud, and
Spring Modulith BOM coordinates and these inherited component families where
the current build actually resolves them:

| Component family | Resolution evidence |
| --- | --- |
| Spring Framework | Library runtime and test classpaths |
| Spring Security | `service-core` and `service-web` test classpaths; compile-only APIs are also resolved through compile classpaths |
| Embedded Tomcat | `service-web` test runtime, which exercises the servlet stack |
| Netty | `service-web` test runtime, which exercises the reactive stack |
| Jackson | `service-core` and `service-web` runtime and test classpaths |

The entry count and resolved versions will change as the checked-in dependency
selection changes; they are evidence from onboarding, not a desired-version
inventory. A successful local snapshot proves generation and transitive
coverage, but only a hosted run after repository activation proves submission
and creates Dependabot alert input.

GitHub's dependency graph reports resolved packages but does not decide which
BOM or override should remediate an inherited finding. A Dependabot alert is
also not proof that an available Spring Boot update fixes every Framework,
Security, Jackson, Tomcat, or Netty advisory. Trace the affected configuration
with Gradle dependency insight and review any override as a service-owner
decision.

## Bot pull request checks

For every Renovate pull request:

1. Keep the shared preset's no-automerge and dashboard-approval behavior.
2. Review the resolved dependency diff, release notes, Java 25 and Spring
   release-train compatibility, and whether an inherited advisory is actually
   remediated.
3. Run the repository-required validation in order:

   ```bash
   ./gradlew clean spotlessApply
   ./gradlew clean build
   ```

4. For platform or shared-library changes, validate all consuming services
   before release as required by
   [versioning and compatibility](versioning-and-compatibility.md). Do not
   publish artifacts or update consumers from an unreviewed bot branch.

Graph generation failures are failures, not clean security results. Preserve
dependency-resolution and submission errors for triage; do not substitute
Maven Local, omit configurations, or enable a hosted Build Scan to make the job
pass.

# Dependency Automation

The workspace-wide operating policy, shared Renovate preset, and failure triage
are owned by
[orchestration's dependency automation guide](../../orchestration/docs/dependency-automation.md).
This document records only the `service-common` integration and review checks.

## Update discovery

`renovate.json` extends
`github>budgetanalyzer/orchestration//renovate-presets/default`. The reference
has no branch suffix, so Renovate inherits the production preset from the
orchestration repository's default branch. Renovate's native Gradle, Gradle
Wrapper, and GitHub Actions managers discover this repository's version
catalog, build scripts, wrapper distribution, and workflow actions. Add
file-specific manager configuration only if a future dependency declaration is
not covered by a native manager.

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

`.github/workflows/dependency-submission.yml` preserves graph submission on
trusted `main` pushes, weekly runs, and manual dispatches. The workflow grants
only `contents: write` at job scope, checks out without persisted credentials,
and uses the official `gradle/actions/dependency-submission` action to generate
and submit the resolved graph directly. The action uses the open-source `basic`
cache provider and runs Gradle with `--no-configuration-cache`.

The action's default resolution task visits all projects and all resolvable
configurations. Do not add project or configuration filters without proving
equivalent coverage: the platform modules contribute imported BOMs, while the
library modules contribute compile, runtime, and test dependency trees.

The submitted graph includes the Spring Boot, Spring Cloud, and Spring Modulith
BOM coordinates plus the dependencies resolved from the platform and library
module configurations. This gives Dependabot alerting visibility into inherited
component families such as Spring Framework, Spring Security, servlet and
reactive runtimes, and Jackson without turning those components into direct
Renovate declarations. A successful hosted workflow run is the proof that the
graph was accepted and can supply Dependabot alerts.

GitHub's dependency graph reports resolved packages but does not decide which
BOM or override should remediate an inherited finding. A Dependabot alert is
also not proof that an available Spring Boot update fixes every Framework,
Security, Jackson, Tomcat, or Netty advisory. Trace the affected configuration
with Gradle dependency insight and review any override as a service-owner
decision.

## Production workflow ownership

`.github/workflows/build.yml` runs for `main` pushes, pull requests targeting
`main`, and manual dispatches. It retains the repository's normal JUnit XML and
library JAR artifacts for seven days and validates publication locally with
`publishToMavenLocal`. These JARs are part of the shared-library validation
contract and must not be removed as deployable-application artifact cleanup.

`.github/workflows/dependency-submission.yml` owns resolved graph generation and
submission. Keep its triggers restricted to trusted `main` execution, preserve
full-project resolution, and do not add evidence archives or alternate
generation-only paths.

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

# Java Coverage Tooling Rollout

## Status

Phases 1 and 2 implemented. Phases 3-5 remain proposed.

## Goal

Add no-subscription Java test coverage tooling across all Java code in the workspace, starting with
JaCoCo report generation everywhere and then layering enforcement, mutation testing, and optional
self-hosted reporting only after the baseline is known.

## Scope

The current Java Gradle projects are discoverable with:

```bash
rg --files -g 'build.gradle.kts' -g 'build.gradle' -g 'pom.xml' /workspace
```

Current Java Gradle repositories:

- `/workspace/service-common`
- `/workspace/permission-service`
- `/workspace/transaction-service`
- `/workspace/currency-service`
- `/workspace/session-gateway`

`service-common` has platform subprojects that do not contain Java runtime code. Coverage should
only apply to JVM code modules such as `service-core` and `service-web`, not to `spring-platform` or
`spring-cloud-platform`.

Self-hosted tools belong under `/workspace/workspace/ai-agent-sandbox` and its devcontainer setup,
not in service repositories.

## Tooling Position

- JaCoCo is the default coverage engine for all Java projects.
- Gradle is the source of truth for local and CI coverage generation and enforcement.
- PIT can be added later for mutation testing of critical code paths.
- Self-hosted SonarQube Community Build or Community Edition can be added later for dashboards, but
  it should ingest Gradle-produced JaCoCo XML instead of replacing Gradle coverage checks.
- Subscription-only coverage services are out of scope.

## Phase 1: JaCoCo Baseline Everywhere

Objective: add JaCoCo report generation to every Java codebase without introducing failing coverage
thresholds yet.

Why report-only first:

- It makes the rollout low risk across all services.
- It produces a real baseline before choosing thresholds.
- It avoids blocking unrelated work because of existing uncovered code.

Implementation steps:

1. Read the nearest `AGENTS.md` in each target repository before editing.
2. Apply the Gradle `jacoco` plugin to every Java service build that does not already have it.
3. Configure `jacocoTestReport` to depend on `test` and emit XML and HTML reports.
4. Configure `test` to finalize by `jacocoTestReport` where that matches the local Gradle style.
5. In `service-common`, apply coverage only to Java library subprojects, excluding platform
   projects.
6. Keep `transaction-service` idempotent by preserving or aligning its existing JaCoCo setup.
7. Update each affected repository's testing documentation with the report command and report path.
8. Run the normal repo build commands after each change.

Expected report paths:

```text
service-common/service-core/build/reports/jacoco/test/html/index.html
service-common/service-web/build/reports/jacoco/test/html/index.html
permission-service/build/reports/jacoco/test/html/index.html
transaction-service/build/reports/jacoco/test/html/index.html
currency-service/build/reports/jacoco/test/html/index.html
session-gateway/build/reports/jacoco/test/html/index.html
```

Suggested Gradle behavior for single-module services:

```kotlin
plugins {
    jacoco
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

tasks.withType<Test> {
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}
```

Suggested `service-common` behavior:

- Configure JaCoCo from the root build for subprojects that apply `java-library`.
- Exclude `spring-platform` and `spring-cloud-platform`.
- Preserve the existing module-level test behavior and publishing configuration.

Acceptance criteria:

- `./gradlew test jacocoTestReport` works in every Java repository.
- JaCoCo XML and HTML reports are generated for every Java code module.
- No new coverage threshold fails builds in Phase 1.
- Documentation in each touched repository describes how to generate and view reports.

Implementation note: Phase 1 configures Gradle report generation only. Coverage
verification remains intentionally unwired until Phase 2 thresholds are chosen
from the generated baseline.

## Phase 2: Coverage Gates

Objective: turn the baseline into enforceable quality gates after Phase 1 reports are reviewed.

Status: implemented with repository-local `jacocoTestCoverageVerification` tasks wired into
`check`.

Implementation steps:

1. Record current line and branch coverage for each module.
2. Pick initial thresholds that prevent regression without requiring a large immediate test-writing
   campaign.
3. Start gates slightly below the Phase 1 baseline so Phase 2 prevents regressions instead of
   forcing immediate broad test-writing.
4. Add `jacocoTestCoverageVerification` to each Java project.
5. Wire coverage verification into `check`.
6. Ratchet thresholds upward over time until shared standards are met.

Initial threshold targets:

| Repository | Line gate | Branch gate | Notes |
|---|---:|---:|---|
| `service-common` | 90% aggregate, or module-specific gates | 75% | Shared library code should stay above the general service baseline. |
| `permission-service` | 80% | 70% | Starts below the current baseline as a regression guard. |
| `transaction-service` | 80% | 75% | Starts below the current baseline as a regression guard. |
| `currency-service` | 90% | 85% | Current baseline supports a higher initial gate. |
| `session-gateway` | 90% | 65% | Branch coverage starts lower because reactive/session/security conditionals need targeted tests. |

Recorded Phase 1 baselines and active Phase 2 gates:

| Repository/module | Baseline line | Baseline branch | Active line gate | Active branch gate | Ratchet path |
|---|---:|---:|---:|---:|---|
| `service-common/service-core` | 81.52% | 72.27% | 80% | 70% | Raise branch gate to at least 75% after targeted utility tests. |
| `service-common/service-web` | 94.20% | 81.27% | 93% | 80% | Ratchet toward 95% line / 85% branch. |
| `permission-service` | 84.60% | 72.50% | 80% | 70% | Ratchet branch coverage after authorization/search edge-case tests. |
| `transaction-service` | 85.16% | 78.59% | 80% | 75% | Ratchet after CSV import/search/soft-delete tests. |
| `currency-service` | 96.43% | 88.71% | 90% | 85% | Ratchet toward critical utility and provider path coverage. |
| `session-gateway` | 92.77% | 67.86% | 90% | 65% | Raise branch gate after reactive session/security conditional tests. |

Coverage improvement priorities:

1. `session-gateway` branch coverage: raise from 67.86% by targeting reactive session and
   security conditionals first.
2. `service-common/service-core` branch coverage: raise from 72.27% by covering core utility,
   logging, sanitizer, and configuration conditionals.
3. `permission-service` branch coverage: raise from 72.50% by covering authorization, search
   edge cases, user sync, and deactivation failure paths.
4. `transaction-service` branch coverage: raise from 78.59% toward an 80% branch gate by covering
   CSV import edge cases, search filters, and soft-delete behavior.
5. `currency-service` branch coverage: keep healthy at 88.71%; prioritize only critical provider,
   messaging, caching, and scheduler paths before broad coverage work.

Line coverage is currently healthy across the rollout. Near-term coverage work should prioritize
branch coverage in `session-gateway`, `service-common/service-core`, and `permission-service`
before raising gates.

Target end state:

- Overall line coverage reaches the documented 80% minimum.
- Branch coverage is enforced separately and ratcheted carefully; avoid a blanket 80% branch gate
  until reactive/security-heavy services have targeted tests.
- Shared libraries and critical utilities trend toward 100% meaningful coverage.
- Controllers, services, exception handling, security helpers, and sanitizers have explicit
  coverage expectations.

Acceptance criteria:

- `./gradlew clean build` runs tests, produces reports, and enforces the agreed threshold.
- Thresholds are documented in each repository.
- Any temporarily lower threshold has a written reason and removal path.

## Phase 3: Workspace Coverage Aggregation

Objective: make it easy to inspect coverage across all Java repositories from one command or script.

Implementation options:

- Add a workspace script that runs coverage in each Java repository and prints report paths.
- Optionally collect JaCoCo XML reports into a local output directory.
- Keep repository-local Gradle tasks authoritative.

Suggested script home:

```text
/workspace/workspace/scripts/
```

Acceptance criteria:

- One command can run coverage across all Java repositories.
- The script skips non-Java and forbidden-write repositories.
- Failures clearly identify the repository and Gradle task that failed.

## Phase 4: Mutation Testing With PIT

Objective: measure test effectiveness, not only executed lines.

Initial targets:

- `service-common/service-core`
- `service-common/service-web`
- Security, logging sanitizer, exception handling, and core utility packages in consuming services

Implementation steps:

1. Add the Gradle PIT plugin to selected modules.
2. Start with narrow package filters to keep runtime manageable.
3. Run PIT separately from normal `check` at first.
4. Add documented thresholds only after the mutation baseline is understood.

Acceptance criteria:

- PIT reports are generated for selected high-value modules.
- Mutation testing is documented as a deeper quality signal than line coverage.
- PIT does not make ordinary local build feedback unreasonably slow.

## Phase 5: Optional Self-Hosted Dashboard

Objective: provide a no-subscription dashboard for coverage and code quality trends.

Placement:

```text
/workspace/workspace/ai-agent-sandbox
```

Candidate:

- Self-hosted SonarQube Community Build or Community Edition.

Implementation steps:

1. Extend the ai-agent-sandbox devcontainer or compose setup with the dashboard service.
2. Store local dashboard data in a named Docker volume, not in service repositories.
3. Configure services to publish JaCoCo XML through Gradle.
4. Add local scripts for analysis runs.
5. Document ports, startup commands, credentials, and teardown.

Constraints:

- No paid subscriptions.
- No hosted SaaS dependency.
- Gradle coverage gates remain the source of truth for pass/fail.
- Dashboard setup must not require changes in `/workspace/orchestration` or
  `/workspace/budget-analyzer-web`.

Acceptance criteria:

- The dashboard starts from the ai-agent-sandbox devcontainer environment.
- It can ingest JaCoCo XML from all Java repositories.
- The setup is fully local and reproducible from documented commands.

## Verification Commands

Per repository:

```bash
./gradlew clean spotlessApply
./gradlew clean build
./gradlew test jacocoTestReport
```

Workspace discovery:

```bash
rg --files -g 'build.gradle.kts' -g 'build.gradle' -g 'pom.xml' /workspace
rg -n "jacoco|pitest|sonarqube|coverage" /workspace/*/build.gradle.kts /workspace/service-common
```

## Open Decisions

- Whether Phase 2 should enforce one shared threshold immediately or per-repository ratcheting
  thresholds.
- Whether branch coverage should be enforced alongside line coverage.
- Whether workspace aggregation should be a simple shell script or a small Gradle convention shared
  through copy/paste until a dedicated build convention exists.
- Whether SonarQube is worth the local operational overhead after Gradle reports and PIT are in
  place.

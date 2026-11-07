# ADR-0005: SonarQube Cloud quality gate on new code

- Status: accepted
- Date: 2026-11-07

## Context
Tests alone do not catch bugs, code smells, security hotspots or untested new code before merge. This is a money-handling service, so we want an automatic check on every PR. Holding the whole project to a coverage number would punish new work for old gaps and encourage tests written only to raise a figure.

## Options considered
1. No static analysis, rely on review – cheapest, but review misses things and is not repeatable.
2. JaCoCo `check` rule in the build – enforces coverage, but gives no bug, vulnerability or duplication analysis and has no PR view.
3. SonarQube Cloud (free for public repos) with a quality gate on new code – one tool for bugs, vulnerabilities, hotspots, duplication and coverage, shown on each PR.

## Decision
Option 3, using `sonar-maven-plugin` and the JaCoCo XML report from `mvn verify`.

Quality gate, applied to **new code** only (the "Clean as You Code" approach):

| Condition | Threshold | Why |
|---|---|---|
| New bugs | 0 | A new bug is cheap to fix now and costly later. |
| New vulnerabilities | 0 | Same, and security-relevant for a payments service. |
| New security hotspots reviewed | 100% | Every hotspot gets an explicit human decision. |
| Coverage on new code | at least 80% | A floor, not a target. High enough to force real tests, low enough to avoid tests that only chase the number. |
| Duplication on new code | at most 3% | Keeps copy-paste out of the ledger and transfer logic. |

Coverage applies to new and changed code only, not the whole project. Config classes and the application entry point (`WalletApplication`) are excluded from coverage, not from analysis.

Wiring:
- Project key and organization are `pom.xml` properties (`sonar.projectKey`, `sonar.organization`); the workflow does not hard-code them.
- CI runs the scan after the tests in the `build-and-test` job. The token is the `SONAR_TOKEN` repository secret, which only the owner adds.
- The scan step is skipped when the secret is empty (forked PRs), so the build stays green without it.
- The gate itself is configured in SonarQube Cloud, not in the repo. The result appears on each PR as a "SonarCloud Code Analysis" check, which is the check to mark as required on `develop` and `main`.

## Consequences
- Easier: untested or risky new code is flagged on the PR; thresholds apply only to code a PR touches.
- Harder: PRs can fail the gate for reasons unrelated to tests (duplication, hotspots); the owner must keep the SonarQube Cloud gate in line with this table.
- Owner-only setup: sign in to SonarQube Cloud with GitHub, import the `vivid-pay` organization and the `wallet-service` project, turn off Automatic Analysis (CI analysis is used), generate the token, add `SONAR_TOKEN`, and require the Sonar check in branch protection.
- Revisit later: a higher coverage bar for the `ledger` and `transfers` modules, and mutation testing (PIT) on them.

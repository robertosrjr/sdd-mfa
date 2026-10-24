# Implementation Plan: Aprovação de Operações Sensíveis com Step-Up

**Branch**: `001-step-up-transaction-approval` | **Date**: 2026-10-22 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/001-step-up-transaction-approval/spec.md`

## Summary

Add a Java/Spring REST authorization flow that evaluates authentication strength and
freshness, business authorization, and user approval of an immutable operation intent as
separate decisions. A successful flow permits one execution; denial, rejection, failure to
complete the interactive approval, or changed operation details prevents execution.
Repeated requests and uncertain downstream results are reconciled without blindly repeating
business effects. Delegated requests preserve both the acting representative and the
represented subject.

## Technical Context

**Language/Version**: The current `application/pom.xml` compiles with Java release 21;
local validation runs on JDK 25. The original Java 25 compilation target remains an open
platform-alignment item, documented in `analyze.md`.

**Primary Dependencies**: The implementation uses Spring Boot 3.4.2, Spring MVC, JDBC,
Flyway, Micrometer, the OpenTelemetry tracing bridge and Prometheus. Versions are managed
by the existing Spring Boot parent. The original Spring Boot 4.1.1 target is not implemented;
the quality phase does not perform a platform migration.

**Storage**: A durable relational store is the design baseline for operation authorization,
approval consumption, audit references, and idempotency uniqueness. The local default is
in-memory H2 with Flyway migrations V1–V3. A PostgreSQL/Testcontainers concurrency suite exists;
its execution remains pending because Docker is unavailable locally.

**Testing**: JUnit, AssertJ, Mockito, Testcontainers, and ArchUnit are the local Java testing
conventions. Use unit tests for domain and use-case rules, integration tests for durable
atomicity and adapters, contract tests for HTTP behavior, and a small number of end-to-end
tests for the critical journey.

**Target Platform**: Java service exposing a versioned REST interface. The trusted user
interface, identity provider, authorization policy source, and business operation services
are existing or external dependencies; this workspace does not identify their products.

**Project Type**: Backend service / REST API. This is inferred from the Spring, REST, and
platform skills; source and Maven build are present under `application/`.

**Performance Goals**: The specification defines correctness outcomes, not latency or
throughput SLOs. Do not claim a numeric target without workload and service objectives.
Establish a measured baseline and agree production SLOs before launch.

**Constraints**:
- No delayed approval queue: the titular's decision is collected in the same interactive
  flow; inability to complete it cancels the authorization attempt.
- Never retry a non-idempotent business write blindly. Keep an uncertain result distinct
  from confirmed success and reconcile before retry.
- Do not log or emit credentials, access tokens, raw operation payloads, or unnecessary
  personal or financial data. Metrics and trace attributes must have bounded cardinality.
- Avoid provider-specific authentication labels as sole evidence of authenticator strength.
- Apply temporary, policy-defined limits to repeated failed authentication or approval
  attempts without automatically locking the entire account.

**Scale/Scope**: Any sensitive operation type, financial or otherwise. Operation-specific
material details are supplied by each business domain. No throughput, tenant-count, or
retention figures are present in the specification.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle / gate | Plan response | Status |
|---|---|---|
| I. Separate authentication, authorization, and transaction approval | Model and evaluate each as separate evidence and decisions; one cannot substitute for another. | PASS |
| II. Bind approval to exact transaction intent | Bind single-use approval to canonical intent and reject execution if the intent changes. | PASS |
| III. Least privilege and explicit delegation | Preserve actor and subject separately; fail closed on missing or invalid evidence. | PASS |
| IV. Security tests and audit evidence | Test allowed and denied paths, replay, concurrency, changed intent, retries, and partial failures; audit without secrets. | PASS |
| V. Simple, risk-proportionate design | Use the existing identity, policy, UI, and business-operation services through ports; no new broker or asynchronous approval queue. | PASS |
| Security, privacy, and failure paths | Minimize persisted data and telemetry; cancellation and unknown execution outcomes do not permit unverified re-execution. | PASS |
| Observable acceptance and quality gates | Map each feature requirement to an acceptance or verification scenario; define operational SLO after workload discovery. | PASS |

No constitutional exceptions are required.

## Design Decisions

1. **Java/Spring baseline**: Use Java 25 as requested and Spring Boot 4.1.1 as recorded by
   project-specific skills, subject to verifying Java 25 compatibility when the application
   module and build are established. Keep the domain free of framework types. Do not
   introduce reactive processing without a demonstrated I/O-concurrency need.
2. **Hexagonal boundaries**: Keep pure domain rules and state transitions in `domain`;
   implement orchestration in `application`; place REST, persistence, identity, policy,
   and business-service adapters in `infrastructure`.
3. **Durable single-use controls**: Use durable transactional uniqueness and conditional
   state transitions as the source of truth for approval consumption and idempotency.
   An in-memory lock or cache is not sufficient across multiple service instances.
4. **Intent integrity**: Build a canonical representation of the operation's material
   details and bind approval to its version and digest. Compare the current operation
   intent with the approved intent immediately before execution. The business adapter must
   apply that same version of the intent.
5. **No asynchronous approval state**: Collect human approval in the active interaction.
   A user timeout, rejection, or inability to complete the same flow cancels the
   authorization attempt. `OUTCOME_UNKNOWN` concerns only business execution whose result
   cannot yet be confirmed; it is not a pending user approval.
6. **Retry and duplicate handling**: Require an idempotency key for mutation requests.
   Scope its uniqueness to the authenticated client/subject and operation; compare a
   canonical request fingerprint on reuse. Return the original terminal result for a
   matching completed request, reject mismatched reuse, and report an active concurrent
   attempt without starting another execution.
7. **HTTP and errors**: Use a versioned REST contract, RFC 9470 step-up challenge semantics
   where applicable, and RFC 9457 Problem Details for API errors. Do not expose provider
   details or secrets in error bodies.
8. **Observability and privacy**: Emit stable, low-cardinality security-decision and
   operation-outcome signals. Correlate through technical trace identifiers, not user
   identity. Keep raw operation details out of logs, metrics, and spans.

## Architecture Overview

The protected business service remains the authority for operation data and execution.
The authorization flow obtains a consistent operation intent, checks identity strength and
recency, asks the applicable policy service for a decision, presents the same material
details for explicit user approval, and authorizes the business service to execute only
after all required decisions pass.

The approval record binds the subject, optional delegated actor, operation identity,
canonical intent version/digest, policy decision, expiration, and consumption outcome.
Sensitive raw details remain with the business service or are retrieved through a
least-privilege port when needed for display; the authorization component stores only what
is required for integrity, decision evidence, and audit.

Cross-service execution cannot rely on a local database transaction alone. A durable
execution-attempt record and business-service idempotency/reconciliation contract are
required. A confirmed terminal result is replayed for a duplicate request; an unknown
result is reconciled before another business write.

## Project Structure

### Documentation (this feature)

```text
specs/001-step-up-transaction-approval/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── operation-authorization.openapi.yaml
└── tasks.md                 # Generated later by /speckit-tasks
```

### Source Code (application directory; proposed, not present yet)

```text
application/
├── pom.xml
├── src/main/java/
│   ├── domain/
│   │   ├── model/
│   │   ├── service/
│   │   └── exception/
│   ├── application/
│   │   ├── port/in/
│   │   ├── port/out/
│   │   └── usecase/
│   └── infrastructure/
│       ├── adapter/in/web/
│       ├── adapter/out/persistence/
│       ├── adapter/out/identity/
│       ├── adapter/out/policy/
│       ├── adapter/out/operation/
│       └── config/
└── src/test/java/
    ├── unit/
    ├── integration/
    ├── contract/
    └── architecture/
```

**Structure Decision**: The Java application will reside inside the `application/` directory, keeping the repository root clean for specifications, documentation, and tooling. Use the Java package boundaries prescribed by `.claude/skills/architecture-guidance/SKILL.md`.

## Complexity Tracking

No constitutional violations or additional projects are introduced. The durable
authorization and idempotency records are justified by single-use approval, concurrent
requests, and uncertain execution outcomes; an in-memory-only design would not preserve
those guarantees across instances or restarts.

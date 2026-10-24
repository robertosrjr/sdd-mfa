---
description: "Task list for Sensitive Operation Authorization with Step-Up"
---

# Tasks: Aprovação de Operações Sensíveis com Step-Up

**Input**: Design documents from `specs/001-step-up-transaction-approval/`
**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [data-model.md](data-model.md), [contracts/operation-authorization.openapi.yaml](contracts/operation-authorization.openapi.yaml), [research.md](research.md), [quickstart.md](quickstart.md), [.specify/memory/constitution.md](../../.specify/memory/constitution.md)

**Tests**: Test tasks are included per project constitution (Principle IV: Security tests and audit evidence) and testing strategy guidance. Write tests first where indicated.

**Organization**: Tasks are grouped by phase and user story to enable independent implementation and testing of each story.
**Application Directory**: All Java application source code, build files, and tests reside under the `application/` directory.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (`US1`, `US2`, `US3`)
- File paths are relative to repository root (`application/...`)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization, build configuration, and architectural guardrails in `application/`.

- [ ] T001 Create project build descriptor with Java 25, Spring Boot 4.x dependencies (Spring Web MVC, Validation, Actuator, Jackson) and testing framework (JUnit 5, AssertJ, Mockito, Testcontainers, ArchUnit) in `application/pom.xml`. **Partial**: build and tests exist on release 21 / Spring Boot 3.4.2; original platform target is still open (F2 in `analyze.md`).
- [x] T002 [P] Create hexagonal package directory structure (`domain/`, `application/`, `infrastructure/`, `test/`) in `application/src/main/java/` and `application/src/test/java/` per `plan.md`
- [x] T003 [P] Configure ArchUnit architectural test suite enforcing hexagonal boundaries (domain isolation from Spring/infrastructure dependencies) in `application/src/test/java/architecture/HexagonalArchitectureTest.java`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure, domain primitives, error models, and audit interfaces required before ANY user story can be implemented.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [x] T004 Define shared domain value objects (`SubjectId`, `ActorId`, `OperationId`, `OperationType`, `IntentDigest`, `IntentVersion`) and base domain exceptions in `application/src/main/java/domain/model/` and `application/src/main/java/domain/exception/DomainException.java`
- [x] T005 [P] Implement RFC 9457 Problem Details error models and global controller advice exception handler in `application/src/main/java/infrastructure/adapter/in/web/GlobalExceptionHandler.java`
- [x] T006 [P] Create database migration schema for authorization attempts, approval consumption records, and idempotency state in `application/src/main/resources/db/migration/V1__init_authorization_schema.sql`
- [x] T007 [P] Implement Audit Event outbound port and security-safe logging adapter (strictly redacting secrets, bearer tokens, and PII per Constitution Principle IV) in `application/src/main/java/application/port/out/AuditEventPort.java` and `application/src/main/java/infrastructure/adapter/out/audit/LoggingAuditAdapter.java`
- [x] T008 [P] Implement Rate Limit / Temporary Lockout port and adapter to enforce failure limits per operation without global account lock (FR-015) in `application/src/main/java/application/port/out/RateLimitPort.java` and `application/src/main/java/infrastructure/adapter/out/policy/TemporaryLockoutAdapter.java`

**Checkpoint**: Foundation ready — user story implementation can now begin in parallel or sequentially.

---

## Phase 3: User Story 1 - Aprovar Operação Sensível com Step-Up (Priority: P1) 🎯 MVP

**Goal**: Evaluate authentication strength/freshness, business authorization, and explicit human approval of canonical material intent as separate decisions. Permit exactly one execution on success, challenge with RFC 9470 on insufficient auth, and fail closed on denial, cancellation, or changed intent.

**Independent Test**: Send POST `/api/v1/operation-authorizations` under scenarios: (1) weak authentication returns 401 with `WWW-Authenticate` RFC 9470 challenge; (2) denied policy returns 403; (3) changed material intent returns rejection; (4) valid approval executes operation once and marks approval consumed.

### Tests for User Story 1 (Write first, verify failure before implementation)

- [x] T009 [P] [US1] Unit test for canonical intent serialization and SHA-256 digest calculation in `application/src/test/java/unit/domain/IntentDigestTest.java`
- [x] T010 [P] [US1] Unit test for authentication freshness/strength evaluation and RFC 9470 step-up challenge logic in `application/src/test/java/unit/domain/AuthenticationContextTest.java`
- [x] T011 [P] [US1] Unit test for `OperationAuthorization` and `TransactionApproval` state machine and single-use atomic consumption in `application/src/test/java/unit/domain/OperationAuthorizationTest.java`
- [x] T012 [P] [US1] Contract test for POST `/api/v1/operation-authorizations` validating HTTP 200, 401 RFC 9470, 403, and RFC 9457 schemas in `application/src/test/java/contract/OperationAuthorizationContractTest.java`

### Implementation for User Story 1

- [x] T013 [P] [US1] Implement domain aggregate models `OperationAuthorization`, `TransactionApproval`, `AuthenticationEvidence`, and `AuthorizationDecision` in `application/src/main/java/domain/model/`
- [x] T014 [US1] Implement Canonical Intent Digest calculation service in `application/src/main/java/domain/service/IntentCanonicalizer.java`
- [x] T015 [P] [US1] Define outbound ports for identity verification (`IdentityProviderPort`), policy check (`PolicyAuthorizationPort`), and downstream business execution (`BusinessOperationPort`) in `application/src/main/java/application/port/out/`
- [x] T016 [US1] Implement primary application use case `AuthorizeSensitiveOperationService` orchestrating auth check, policy eval, intent matching, and single-use consumption in `application/src/main/java/application/usecase/AuthorizeSensitiveOperationService.java`
- [x] T017 [US1] Implement relational persistence repository adapter with atomic conditional state updates (`consumedAt` CAS) in `application/src/main/java/infrastructure/adapter/out/persistence/JpaOperationAuthorizationRepository.java`
- [x] T018 [US1] Implement REST controller endpoint for POST `/api/v1/operation-authorizations` with request DTO validation and RFC 9470 response headers in `application/src/main/java/infrastructure/adapter/in/web/OperationAuthorizationController.java`
- [x] T019 [US1] Integration test with Testcontainers verifying end-to-end authorization journey, single-use approval, and failure paths in `application/src/test/java/integration/AuthorizeSensitiveOperationIntegrationTest.java`

**Checkpoint**: User Story 1 (MVP) is fully functional and testable independently.

---

## Phase 4: User Story 2 - Confirmar Operação Iniciada por Representante Delegado (Priority: P2)

**Goal**: Preserve actor and subject attribution separately on delegated requests, present explicit initiator details to the titular, mandate human approval during the active flow, and prevent agent self-approval or impersonation.

**Independent Test**: Submit a delegated operation with distinct `actorId` and `subjectId`. Verify presentation data displays both actor and subject; verify that agent-signed approval without titular consent is rejected; verify attribution is logged in audit records.

### Tests for User Story 2

- [x] T020 [P] [US2] Unit test for delegation domain invariants (actor vs subject attribution, rejection of agent self-approval when human approval required) in `application/src/test/java/unit/domain/DelegationAttributionTest.java`
- [x] T021 [P] [US2] Contract test for delegated operation request/response schemas in `application/src/test/java/contract/DelegatedOperationContractTest.java`

### Implementation for User Story 2

- [x] T022 [P] [US2] Extend domain models `OperationAuthorization` and `TransactionApproval` with `DelegationAttribution` value object and validation logic in `application/src/main/java/domain/model/DelegationAttribution.java`
- [x] T023 [US2] Implement delegation policy validation service rejecting missing attribution and agent self-approval in `application/src/main/java/application/usecase/DelegatedAuthorizationValidator.java`
- [x] T024 [US2] Update REST controller request DTOs and mapping to parse and validate `actorId` and delegation context in `application/src/main/java/infrastructure/adapter/in/web/dto/AuthorizationRequestDto.java`
- [x] T025 [US2] Integration test for delegated operation flow verifying actor attribution in audit trail and enforcement of human titular consent in `application/src/test/java/integration/DelegatedOperationIntegrationTest.java`

**Checkpoint**: User Stories 1 and 2 both operate and test independently.

**Validation (2026-10-24)**: `mvn -o clean test` in `application/`: 61 tests, zero failures/errors/skips.
US2 adds 8 domain, 5 HTTP contract and 19 H2 integration cases. V2 stores actor/approver and human
consent evidence; legacy approvals without that evidence require fresh trusted consent. External
identity/policy/business adapters remain local simulations; the approval UI is an external integration.

---

## Phase 5: User Story 3 - Recuperar Resultado de Tentativa Interrompida e Concorrência (Priority: P3)

**Goal**: Prevent duplicate business execution and clarify interrupted outcomes via durable idempotency keys scoped to client/operation, request fingerprint validation, terminal result replay, concurrent execution locking, and reconciliation of uncertain outcomes (`OUTCOME_UNKNOWN`).

**Independent Test**: (1) Re-send same request with same idempotency key and verify replayed terminal response without duplicate business write; (2) Re-send same key with altered payload and verify 409 Conflict; (3) Concurrently fire two identical requests and verify exactly one execution; (4) Simulate downstream timeout, verify 202 Accepted, and verify reconciliation before subsequent attempts.

### Tests for User Story 3

- [x] T026 [P] [US3] Unit test for `IdempotencyRecord` state machine transitions (`IN_PROGRESS`, `SUCCEEDED`, `FAILED`, `OUTCOME_UNKNOWN`, `RECONCILING`) in `application/src/test/java/unit/domain/IdempotencyRecordTest.java`
- [x] T027 [P] [US3] Unit test for canonical request fingerprint hashing in `application/src/test/java/unit/infrastructure/RequestFingerprinterTest.java`
- [ ] T028 [P] [US3] Integration test with Testcontainers for concurrent requests using identical idempotency key verifying mutual exclusion. H2 assertions implemented and passing in `application/src/test/java/com/mfa/stepup/integration/ConcurrentExecutionTest.java`; PostgreSQL variant implemented in `PostgreSQLConcurrentExecutionTest.java`, execution pending Docker availability.

### Implementation for User Story 3

- [x] T029 [P] [US3] Implement `IdempotencyRecord` domain model, state transitions, and scope validation in `application/src/main/java/domain/model/IdempotencyRecord.java`
- [x] T030 [US3] Implement durable idempotency port and relational repository adapter with atomic locking and fingerprint verification in `application/src/main/java/application/port/out/IdempotencyPort.java` and `application/src/main/java/infrastructure/adapter/out/persistence/JpaIdempotencyRepository.java`
- [x] T031 [US3] Implement Idempotency filter / web handler validating `Idempotency-Key` header in `application/src/main/java/infrastructure/adapter/in/web/IdempotencyFilter.java`; durable locking and replay are enforced by the application use case and `IdempotentExecutionTransactions` for both HTTP and internal callers.
- [x] T032 [US3] Implement reconciliation use case and outbound port for downstream business services (`OUTCOME_UNKNOWN` -> `RECONCILING`) in `application/src/main/java/application/usecase/ReconcileOperationOutcomeService.java` and `application/src/main/java/application/port/out/BusinessOperationReconciliationPort.java`
- [x] T033 [US3] Integration test for downstream timeout handling (HTTP 202 Accepted) followed by query/reconciliation replay in `application/src/test/java/integration/IdempotencyReconciliationIntegrationTest.java`

**Checkpoint**: US3 implementation and H2 verification complete. PostgreSQL execution of T028 remains pending.

**Validation (2026-10-24)**: `mvn -o clean test`: 92 discovered tests, 90 passed, zero failures/errors,
2 PostgreSQL tests skipped because Docker is unavailable. HTTP tests verify mandatory key validation,
409 on changed payload and 202 followed by reconciled 200. Integration tests verify committed approval
before dispatch, concurrent exclusion, durable replay, query-only crash recovery, and stale-owner fencing.

V3 adds durable execution ownership and configurable leases/retention (defaults: PT30S/PT24H).
An execution is never re-submitted after an uncertain result, even after retention expiry. The local
reconciliation adapter returns unknown until a real read-only business adapter is supplied.
Regression tests also reproduced and fixed delimiter/type/null/nesting ambiguity in the previous
intent canonicalizer; new encoding is `mfa-intent-v2`, requiring fresh approval for legacy digests.
External identity binding and real business integrations remain outside the local simulated adapters.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Observability, security privacy audits, documentation, and quality gate analysis.

- [x] T034 [P] Integration tests verifying absence of sensitive sentinels in audit/logs, real OpenTelemetry spans and Prometheus output in `application/src/test/java/com/mfa/stepup/integration/AuditPrivacyComplianceTest.java` and `AuthorizationObservabilityIntegrationTest.java`.
- [x] T035 [P] Configure Micrometer metrics with bounded-cardinality counters (`authorization_decision_total` in Prometheus) and latency timers in `application/src/main/java/infrastructure/config/MetricsConfig.java`.
- [x] T036 Run local real-HTTP acceptance verification in `AuthorizationAcceptanceTest` against `quickstart.md`; external identity/consent/business integration criteria remain open gates in `analyze.md`.
- [x] T037 Perform SDD Quality Gate Audit and generate bidirectional traceability matrix (FR-001–FR-015 ↔ T001–T036) in `specs/001-step-up-transaction-approval/analyze.md`.

**Validation (2026-10-24)**: `mvn -o clean test`: 102 discovered tests, 100 passed, zero failures/errors,
2 PostgreSQL tests skipped without Docker. Tests capture real SDK spans and logs, assert protected
audit references, verify generic error handling and bounded metric labels, and call a real HTTP server.
The audit explicitly preserves open T001 platform alignment, T028 PostgreSQL execution and external
integration gates. No production-readiness or legal-compliance certification is claimed.

---

## Dependencies & Execution Order

### Phase Dependencies

```
Phase 1: Setup (application/)
    └──► Phase 2: Foundational (BLOCKS all User Stories)
            ├──► Phase 3: User Story 1 (P1 - MVP)
            │       └──► Phase 4: User Story 2 (P2 - Delegation)
            │               └──► Phase 5: User Story 3 (P3 - Idempotency & Concurrency)
            │                       └──► Phase 6: Polish & Cross-Cutting Concerns
```

### User Story Dependencies

- **User Story 1 (P1)**: Starts immediately after Phase 2 (Foundational). Has no dependencies on other stories.
- **User Story 2 (P2)**: Builds upon the `OperationAuthorization` and presentation structure established in US1, extending them with actor attribution and delegation validation.
- **User Story 3 (P3)**: Wraps execution invocations with idempotency keys and reconciliation semantics, building upon the execution flow established in US1.

### Within Each User Story

1. Write tests first and verify they fail before implementing production code.
2. Domain models and value objects first.
3. Outbound ports and application services next.
4. Inbound adapters (REST controllers) and persistence adapters.
5. End-to-end / integration tests with Testcontainers.

---

## Parallel Opportunities

- **Phase 1**: `T002` (directory structure) and `T003` (ArchUnit test) can run in parallel after `T001`.
- **Phase 2**: `T005` (Problem Details), `T006` (Flyway schema), `T007` (Audit port), and `T008` (Rate limit port) can run in parallel after `T004`.
- **Phase 3 (US1)**: Test tasks `T009`, `T010`, `T011`, `T012` can be written in parallel. Ports `T015` and domain models `T013` can be developed in parallel.
- **Phase 4 (US2)**: Test tasks `T020`, `T021` and model `T022` can run in parallel.
- **Phase 5 (US3)**: Test tasks `T026`, `T027`, `T028` and model `T029` can run in parallel.
- **Phase 6**: `T034` (Privacy test) and `T035` (Metrics) can run in parallel.

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete **Phase 1: Setup** (Project build and hexagonal guardrails in `application/`).
2. Complete **Phase 2: Foundational** (Domain primitives, error handling, audit, and schema).
3. Complete **Phase 3: User Story 1** (Core Step-Up authorization and single-use execution).
4. **STOP and VALIDATE**: Run `AuthorizeSensitiveOperationIntegrationTest` and contract tests. Verify MVP functionality independently.

### Incremental Delivery

1. Foundation ready (Phases 1 & 2 in `application/`).
2. Deliver US1 (MVP) ➔ Core sensitive operation step-up authorization.
3. Deliver US2 ➔ Delegated operations with actor attribution and mandatory human consent.
4. Deliver US3 ➔ Durable idempotency, duplicate protection, concurrency control, and outcome reconciliation.
5. Deliver Polish ➔ Metrics, privacy audit compliance, and SDD audit report (`analyze.md`).

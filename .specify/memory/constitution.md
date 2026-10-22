<!--
Sync Impact Report
Version change: unratified template → 1.0.0
Modified principles:
  Template principle 1 → I. Separate Authentication, Authorization, and Transaction Approval
  Template principle 2 → II. Bind Approval to Exact Transaction Intent
  Template principle 3 → III. Enforce Least Privilege and Explicit Delegation
  Template principle 4 → IV. Verify Security Behavior with Tests and Audit Evidence
  Template principle 5 → V. Prefer Simple, Risk-Proportionate Design
Added sections: Security and Authorization Constraints; Engineering Workflow and Quality Gates
Removed sections: none
Follow-up TODOs: none
-->
# MFA Constitution

## Core Principles

### I. Separate Authentication, Authorization, and Transaction Approval
Every protected operation MUST independently establish that the requester is authenticated
with sufficient strength and recency, is authorized by policy for the requested action, and
has approved the specific business operation when transaction approval is required. MFA or
a recently elevated session MUST NOT, by itself, be treated as permission to execute a
transaction. This separation prevents identity evidence from being mistaken for business
consent.

### II. Bind Approval to Exact Transaction Intent
When an operation requires user approval, the approval MUST be bound to the material
transaction details presented for confirmation, including the relevant parties, amount,
currency, and source or destination where applicable. The system MUST reject or require new
approval if those details change before execution, and MUST prevent an approval from being
replayed or consumed more than once. Retries and uncertain outcomes MUST NOT cause duplicate
execution. These controls prevent time-of-check/time-of-use substitution and replay.

### III. Enforce Least Privilege and Explicit Delegation
Each user, service, and delegated agent MUST be authorized only for the actions and resources
needed for its stated purpose. A delegated request MUST retain distinguishable information
about the acting principal and the subject on whose behalf it acts. Delegation or agent
authentication MUST NOT replace a required authorization decision or human approval.
Sensitive actions MUST fail closed when required identity, policy, or approval evidence is
missing, invalid, expired, or insufficient.

### IV. Verify Security Behavior with Tests and Audit Evidence
Security-sensitive behavior MUST have repeatable tests for both allowed and denied
outcomes. Tests MUST cover applicable authentication strength and recency, authorization
denials, transaction-detail changes, approval reuse, and retry or partial-failure behavior.
Auditable decisions MUST record enough non-secret context to identify the principal,
delegated actor when present, operation, decision, and outcome. Credentials, tokens, and
unnecessary sensitive transaction data MUST NOT be written to logs.

### V. Prefer Simple, Risk-Proportionate Design
Each change MUST address a stated user or security requirement with the least complexity
that satisfies it. New protocols, services, dependencies, and abstractions MUST have a
documented need and a verification strategy. Designs MUST preserve clear trust boundaries
and MUST NOT claim security guarantees that have not been verified. This keeps the system
reviewable and reduces unexamined attack surface.

## Security and Authorization Constraints

- Authentication evidence MUST be evaluated against the operation's required assurance
  and freshness; a provider-specific level or label alone MUST NOT be treated as proof of
  a particular authenticator method.
- Authorization policy MUST be evaluated independently of authentication and transaction
  approval. A successful step-up MUST NOT override a policy denial.
- Approval interfaces MUST clearly present the material details the user is approving.
  The details used for execution MUST match the details that were approved.
- An approval MUST be scoped to its intended operation, expire according to a defined
  policy, and be consumed atomically. Changed transaction details require fresh approval.
- Delegated or agent-initiated sensitive operations MUST preserve attribution and require
  human confirmation whenever policy requires it. An agent MUST NOT approve its own
  operation on behalf of a user.
- Recovery, cancellation, timeout, and partial-failure paths MUST preserve the same
  authorization and transaction-integrity guarantees as the normal path.
- Implementations MUST protect credentials and approval evidence in transit and at rest,
  and MUST avoid exposing secrets or unnecessary personal data in logs and errors.

## Engineering Workflow and Quality Gates

- Each feature MUST define observable acceptance criteria before implementation. Technical
  design choices belong in the plan and MUST be justified by project needs and these
  principles.
- Changes to authentication, authorization, delegation, approval, or transaction execution
  MUST include a review of relevant abuse cases and tests for the security boundaries they
  affect.
- Verification MUST cover the changed behavior and relevant regressions. For transaction
  flows, this includes alteration, replay, concurrent use, retry, and ambiguous execution
  outcomes where applicable.
- Reviews MUST verify that the implementation follows this constitution, that audit
  records do not expose secrets, and that tests substantiate the stated security behavior.
- Exceptions MUST identify the affected principle, rationale, risk owner, compensating
  controls, and an expiration or review date. An exception MUST NOT silently weaken
  protections for unrelated operations.

## Governance

This constitution governs project requirements, plans, implementation, tests, and reviews.
Amendments MUST be made in this file, explain the reason for the change, and be reviewed
for impact on existing features and their tests before adoption.

The version follows semantic versioning: MAJOR for incompatible changes to principles or
governance, MINOR for new or materially expanded principles or sections, and PATCH for
clarifications that do not change requirements. Every implementation review MUST check
compliance with applicable principles and document any approved exception under the
Engineering Workflow and Quality Gates.

**Version**: 1.0.0 | **Ratified**: 2026-10-22 | **Last Amended**: 2026-10-22

# ADR-0001: Health Care and Social Services Regulatory Administration Actor Architecture

## Status

Implemented (`:maturity :implemented` in `kotoba-lang/industry`)

## Context

ISIC Rev.5 8412 defines regulatory administration of health care, education, cultural services, and social service providers. A regulatory agency oversees hundreds or thousands of facilities (hospitals, schools, care homes, community centers) and must manage:

- Provider registration and verification
- Periodic inspection scheduling and coordination
- Compliance report intake and tracking
- Violation logging and escalation

Current state-of-practice: manual spreadsheets, closed regulatory SaaS (high cost, data lock-in, no audit trail transparency to the providers themselves).

## Decision

Build a stateful actor (langgraph-clj StateGraph) that encapsulates regulatory advisory logic (what to propose) and governance logic (what to allow) as separate, testable components. The actor:

1. Accepts regulatory/compliance requests (inspection scheduling, compliance reporting, violation logging, licensing-application drafting)
2. Delegates proposal generation to a swappable `Advisor` (deterministic mock or LLM-backed)
3. Routes every proposal through a pure `Governor` function that enforces hard invariants (unregistered provider → hard hold, any license issuance/suspension/revocation attempt → permanent hold) and escalation invariants (violations, licensing applications, low confidence → human interrupt-before)
4. Commits only if governor approves
5. Logs every proposal/verdict/disposition to an append-only audit ledger, regardless of outcome

## Invariants

### Hard (`:hard? true` → `:hold`, unreversible, no path to approval)

1. **Provider provenance**: Request's provider must be registered in store
2. **No actuation**: Proposal `:effect` must always be `:propose` (never `:commit`, `:dispatch`, etc.)
3. **Scope boundary**: Closed allowlist of operations:
   - `:register-provider` — on-boarding new facility
   - `:schedule-inspection` — schedule an inspection (does not perform it)
   - `:log-compliance-report` — intake and log a compliance report
   - `:draft-licensing-application` — draft application text (does not issue)
   - `:flag-violation` — log a violation and escalate
   
   **Permanently forbidden**:
   - License issuance, suspension, revocation
   - Binding regulatory determinations
   - Access to protected personal data (HIPAA, FERPA, etc.)
   - Facility inspection performance (scheduling only)

### Escalation (`:escalate? true` → `:request-approval`, human interrupt-before)

1. **Violation flag**: All violation flagging escalates (operator cannot auto-approve)
2. **Licensing application**: Drafting licensing applications escalates (high stakes, human review)
3. **Low confidence** (< 0.6): LLM or mock advisor confidence below threshold

## Governance Layer

The `RegulatoryGovernor` is a **pure function** `check(request, context, proposal, store) -> verdict`, wired as its own `:govern` node in the StateGraph. It:

- Reads from store (vendor registration, records, ledger) but never writes
- Computes hard violations, escalation flags, confidence assessment
- Returns verdict for the `:decide` node to route the graph

The GraphPath graph conditionally routes on verdict:

```text
:intake -> :advise -> :govern -> :decide -+-> :commit           (ok? true)
                                           +-> :request-approval  (escalate? true, interrupt-before)
                                           +-> :hold              (hard? true)
```

## Reference Implementation

- `Store` protocol: `MemStore` (in-memory) swappable with Datomic/kotoba-server
- `Advisor` protocol: `mock-advisor` (deterministic) or `llm-advisor` (LLM-backed)
- `RegulatoryGovernor/check`: pure function, stateless, no I/O
- `StateGraph` (langgraph-clj): checkpointed for human-in-the-loop resume

## Testing Strategy

- Governor unit tests: hard violations, escalations, ok-flow
- Actor integration tests: run-request! flow, escalation-resume with `approve!`, ledger audit trail
- Mock advisor (deterministic) for unit testing, llm-advisor swappable for production

## Why Not a Simple REST API?

The StateGraph pattern enables:

1. **Checkpointing**: Long-running request can interrupt at human-approval node, resume after sign-off, without re-running prior nodes
2. **Audit trail**: Every proposal/verdict/disposition is logged, regardless of outcome
3. **Testability**: Governor pure function, can test without store/LLM
4. **Extensibility**: Advisor/Governor/Store are swappable protocols, not hardcoded
5. **Operational safety**: Hard invariants prevent accidental mis-use (closed allowlist, no actuation, no license decisions)

## Future Extensibility

- Add `:inspect-findings` node to validate inspection reports (gated by governor)
- Add `:compliance-summary` to aggregate provider status reports
- Integrate with kotoba-server for persistent store (Datomic backing)
- Swap LLM advisor for different models (Claude, GPT, local models) without changing actor code

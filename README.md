# cloud-itonami-isic-8412

Open Occupation Blueprint for **ISIC Rev.5 8412**: Health Care, Education, Cultural Services, and Social Services Regulatory Administration.

This repository designs a forkable OSS business for a regulatory agency's administration of health care, education, cultural services, and social service providers: a document-handling and verification robot performs provider registration, inspection scheduling, compliance reporting, and violation logging under a governor-gated actor, so a regulatory agency keeps its own licensing records and audit trail instead of renting a closed regulatory SaaS.

## IMPORTANT: SCOPE BOUNDARIES

**This actor is EXPLICITLY NOT a license decision-maker, regulator, or adjudicator — it is a regulatory SUPPORT tool only.**

### What this actor DOES

- Administrative and compliance operations only:
  - Provider/facility registration and verification
  - Inspection scheduling and coordination
  - Compliance-report intake and documentation
  - Violation logging and escalation
  - Licensing-application drafting (on behalf of agency staff, not issuance)
  - Audit trail and regulatory record-keeping

### What this actor DOES NOT (hard boundaries, permanently out of scope)

These operations are **permanently forbidden** — they are not gated by risk level or approval hierarchy, they cannot be escalated for human override, and the actor's proposal vocabulary has no path to construct them. A closed allowlist enforces this at the governance layer:

- **License issuance, suspension, or revocation** — the actor has no authority to issue, suspend, or revoke any license or accreditation; these decisions remain exclusively with human regulators
- **Binding regulatory determinations** — the actor can log violations and escalate them, but cannot itself determine compliance status, impose sanctions, or make enforceable decisions
- **Confidential patient/student/service-user data** — the actor does not access, store, or process personal health, educational, or service records protected by HIPAA, FERPA, or equivalent privacy law
- **Facility inspections** — the actor schedules and coordinates inspections but does not inspect itself or make inspection findings (inspection is performed by human inspectors)

These are not "high-risk operations requiring escalation" — they are entirely outside the actor's design vocabulary. The governor will **permanently :hold** any proposal that touches these categories (it is not a matter of confidence, approval chain, or authority threshold).

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical domain work**. Here a document-handling and verification robot performs provider registration, inspection coordination, and compliance documentation under an actor that proposes actions and an independent **Regulatory Governance Governor** that gates them. The governor never dispatches a robot action itself; `:high`/`:safety-critical` actions (such as drafting licensing applications or flagging violations) require human sign-off.

A live sample of the operator console (robotics safety console, shared template) is rendered in [docs/samples/operator-console.html](docs/samples/operator-console.html) — pure-data HTML output of `kotoba.robotics.ui`.

## Core Contract

```text
regulatory request + provider identity + compliance data
        |
        v
Regulatory Advisor -> Regulatory Governor -> provider registration, inspection, compliance documentation, violation logging, or human approval
        |
        v
robot actions (gated) + regulatory record + compliance record + audit ledger
```

No automated advice can dispatch a regulatory action the governor refuses, register a provider outside its verified scope, issue a license decision, or publish a regulatory record without governor approval and audit evidence.

## Capability layer

Resolves via [`kotoba-lang/industry`](https://github.com/kotoba-lang/industry)
(ISIC `8412`). Required capabilities:

- :robotics
- :identity
- :forms
- :dmn
- :bpmn
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## Reference implementation (`:maturity :implemented`)

Full itonami Actor pattern (per ADR-2607011000 / CLAUDE.md's Actors
section): a real
[`kotoba-lang/langgraph`](https://github.com/kotoba-lang/langgraph)
`StateGraph`, with the Advisor and Governor as distinct graph nodes and
human-in-the-loop interrupt/resume via checkpointing.

```text
:intake -> :advise -> :govern -> :decide -+-> :commit            (:ok? true)
                                           +-> :request-approval   (:escalate? true, interrupt-before)
                                           +-> :hold               (:hard? true)
```

- `src/regulation/store.cljc` — `Store` protocol + `MemStore`:
  registered providers, committed regulatory records, an append-only audit ledger.
- `src/regulation/advisor.cljc` — `Advisor` protocol; `mock-advisor`
  (deterministic, default) proposes a regulatory operation from a
  request; `llm-advisor` wraps a `langchain.model/ChatModel` — either
  way the advisor only ever produces a `:propose`-effect proposal,
  never a committed record, and LLM parse failures always yield
  `confidence 0.0` (forces escalation, never fabricated confidence).
- `src/regulation/governor.cljc` — `RegulatoryGovernor/check`: a pure
  function, wired as its own `:govern` node. Hard invariants
  (unregistered provider, a proposal whose `:effect` isn't `:propose`, any
  proposal touching license decisions or binding regulatory determinations)
  always route to `:hold`. Escalation invariants (violation flagging,
  licensing-application drafting, or low advisor confidence) always route
  to `:request-approval` — an `interrupt-before` node that the graph
  checkpoints and only resumes on explicit human approval
  (`actor/approve!`).
- `src/regulation/actor.cljc` — `build-graph`, `run-request!`,
  `approve!`: the `langgraph.graph/state-graph` wiring itself.

```bash
clojure -M:test
```

This is what backs this repo's `:maturity :implemented` entry in
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry).

## License

AGPL-3.0-or-later.

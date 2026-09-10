(ns regulation.governor
  "RegulatorySocialHealthGovernor — the independent safety/traceability layer
  for the ISIC Rev.5 8412 health care and social services regulatory
  administration actor. Wired as its own `:govern` node in `regulation.actor`'s
  StateGraph, downstream of `:advise` — the Advisor has no notion of provider
  provenance or risk, so this MUST be a separate system able to reject a
  proposal (itonami actor pattern, per ADR-2607011000 / CLAUDE.md Actors
  section).

  `check` is a pure function of (request, context, proposal, store) ->
  verdict; it never mutates the store. The StateGraph's `:decide` node
  routes on the verdict:
    :hard? true                → :hold  (irreversible, no write)
    :escalate? true            → :request-approval (interrupt-before)
    otherwise                  → :commit

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. provider provenance     — the request's provider must be registered.
    2. no-actuation            — proposal :effect must be :propose.
    3. scope-boundary          — proposals touching license issuance,
                                  suspension, revocation, or binding
                                  regulatory determinations NEVER PROCEED
                                  (closed allowlist enforced here + in advisor).

  ESCALATION invariants (:escalate? true, ALWAYS human sign-off):
    4. violation-flag          — flagging violations is always escalated.
    5. licensing-application   — drafting licensing applications requires
                                  human review.
    6. low confidence          (< `confidence-floor`)."
  (:require [regulation.store :as store]
            [regulation.advisor :as advisor]))

(def confidence-floor 0.6)

; Permanently forbidden operation categories
(def ^:private forbidden-ops #{:unknown})

; Escalating operations (require human approval)
(def ^:private escalating-ops #{:flag-violation
                                 :draft-licensing-application})

(defn- hard-violations [{:keys [proposal]} provider-record]
  (cond-> []
    (nil? provider-record)
    (conj {:rule :no-provider :detail "provider not registered"})

    (not= :propose (:effect proposal))
    (conj {:rule :no-actuation :detail "effect must be :propose only (no direct store writes, no direct license decisions)"})

    (contains? forbidden-ops (:op proposal))
    (conj {:rule :scope-boundary
           :detail "operation outside permitted scope (license issuance, suspension, revocation, and binding regulatory determinations are permanently reserved to human regulators)"})))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `regulation.store/Store`. Returns
  `{:ok? bool :violations [...] :confidence n :hard? bool :escalate? bool}`."
  [request context proposal store]
  (let [provider-record (store/provider store (:provider-id request))
        hard (hard-violations {:proposal proposal} provider-record)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        escalating-op? (contains? escalating-ops (:op proposal))]
    {:ok? (and (not hard?) (not low?) (not escalating-op?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? escalating-op?))}))

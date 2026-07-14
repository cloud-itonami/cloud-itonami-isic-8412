(ns regulation.store
  "SSoT for the ISIC Rev.5 8412 health care and social services regulatory
  administration actor. Store is a protocol injected into the
  `regulation.actor` StateGraph — `MemStore` is the default, deterministic,
  zero-dep backend; a Datomic/kotoba-server-backed implementation can be
  swapped in without touching the actor or governor (itonami actor
  pattern, per ADR-2607011000 / CLAUDE.md's Actors section).

  Domain:

    provider — a registered health/education/social-service provider/facility
               (:provider-id, :name, :type, :verified-at)
    record   — a committed regulatory or compliance operating record
               (inspection scheduling, licensing-application drafting,
               compliance-report intake, violation logging) — written ONLY via
               commit-record!, never mutated in place
    ledger   — an append-only audit trail of every proposal/verdict/
               disposition, regardless of outcome (commit or hold)")

(defprotocol Store
  (provider [s provider-id])
  (records-of [s provider-id])
  (ledger [s])
  (register-provider! [s provider])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (provider [_ provider-id] (get-in @a [:providers provider-id]))
  (records-of [_ provider-id] (filter #(= provider-id (:provider-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-provider! [s provider]
    (swap! a assoc-in [:providers (:provider-id provider)] provider) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:providers {} :records [] :ledger []} seed)))))

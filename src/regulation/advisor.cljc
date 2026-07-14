(ns regulation.advisor
  "RegulatorySocialHealthAdvisor — proposes a regulatory or compliance
  operation (register provider, schedule inspection, draft licensing
  application, log compliance report, flag violation) for a registered
  health/education/social-service provider/facility. The advisor is swappable:
  `mock-advisor` (deterministic, default in dev/tests/CI) or `llm-advisor`
  (wraps a real `langchain.model/ChatModel`). Either way the advisor ONLY
  produces a PROPOSAL — it never writes to the store and has no notion of
  provider provenance or risk; `regulation.governor` is the independent
  system that decides whether the proposal may proceed, per the itonami actor
  pattern.

  A proposal is a map:
    {:op :register-provider|:schedule-inspection|:draft-licensing-application
           |:log-compliance-report|:flag-violation
     :effect :propose        ; the advisor NEVER emits a raw store write
     :stake :low|:medium|:high
     :confidence 0.0-1.0
     :rationale str}

  CLOSED ALLOWLIST: the advisor's proposal vocabulary is restricted to
  administrative compliance operations ONLY. Proposals touching issuance,
  suspension, or revocation of licenses/accreditations (reserved to human
  regulators) are structurally impossible — the advisor cannot construct them.

  LLM parse failures always yield `:confidence 0.0` (never fabricate
  confidence), which forces the governor to escalate/hold."
  (:require [clojure.string :as str]))

; Closed allowlist: only these operations are permitted
(def permitted-ops #{:register-provider
                      :schedule-inspection
                      :draft-licensing-application
                      :log-compliance-report
                      :flag-violation})

(defprotocol Advisor
  (-advise [advisor store request] "request -> proposal map"))

(defn- infer
  "Deterministic mock inference: reads the request's declared op/stake
  straight through (a stand-in for what an LLM would extract from free
  text), with a stake-derived confidence. Enforces closed allowlist:
  only permitted-ops are allowed."
  [_store {:keys [op stake] :as request}]
  (if (contains? permitted-ops op)
    {:op op
     :effect :propose
     :stake (or stake :low)
     :confidence (case (or stake :low) :high 0.7 :medium 0.85 :low 0.95)
     :rationale (str "proposed " (name op) " for provider " (:provider-id request))}
    {:op :unknown
     :effect :propose
     :stake :high
     :confidence 0.0
     :rationale "operation not in permitted allowlist (scope boundary — licensing decisions are reserved to human regulators)"}))

(defn mock-advisor []
  (reify Advisor
    (-advise [_ store request] (infer store request))))

(def ^:private system-prompt
  "You are a health care and social services regulatory compliance advisor.
   Given a regulatory or compliance request, propose ONLY ONE of these operations:
   :register-provider, :schedule-inspection, :draft-licensing-application,
   :log-compliance-report, :flag-violation.

   STRICTLY FORBIDDEN: any operation touching issuance, suspension, or revocation
   of licenses or accreditations — these decisions are reserved exclusively to
   human regulators. If the request implies any license decision, respond with
   :confidence 0.0 and :op :unknown.

   Always provide an honest :confidence (0.0-1.0) and a :stake
   (:low/:medium/:high). Never fabricate confidence you don't have.")

(defn- parse-proposal [content]
  (try
    (let [p (read-string content)
          op-valid? (and (map? p) (contains? permitted-ops (:op p)))]
      (if op-valid?
        (assoc p :effect :propose)
        {:op :unknown :effect :propose :confidence 0.0 :stake :high
         :rationale "operation outside permitted allowlist"}))
    (catch #?(:clj Exception :cljs js/Error) _
      {:op :unknown :effect :propose :confidence 0.0 :stake :high
       :rationale "LLM response parse failure"})))

(defn llm-advisor
  "Wraps a `langchain.model/ChatModel`. `gen-opts` is passed through to
  `model/-generate`. Kept decoupled from any concrete model so this ns
  has no hard dependency beyond `langchain.model`'s protocol."
  [chat-model model-generate-fn gen-opts]
  (reify Advisor
    (-advise [_ _store request]
      (let [msgs [{:role :system :content system-prompt}
                  {:role :user :content (str "regulatory request: " (pr-str request))}]
            resp (model-generate-fn chat-model msgs gen-opts)]
        (parse-proposal (:content resp))))))

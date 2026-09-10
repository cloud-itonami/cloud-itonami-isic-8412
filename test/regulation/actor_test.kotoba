(ns regulation.actor-test
  (:require [clojure.test :refer [deftest is]]
            [regulation.actor :as actor]
            [regulation.store :as store]
            [regulation.advisor :as advisor]))

(deftest run-request-ok-flow
  (let [s (store/mem-store {:providers {"P1" {:provider-id "P1" :name "Hospital A" :verified-at "2026-01-01"}}})
        graph (actor/build-graph {:store s :advisor (advisor/mock-advisor)})
        request {:provider-id "P1" :op :schedule-inspection}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :done (:status result)))
    (is (not (nil? (:state result))))
    (let [records (store/records-of s "P1")]
      (is (= 1 (count records)))
      (is (= :schedule-inspection (:op (first records)))))))

(deftest run-request-hard-hold-unregistered
  (let [s (store/mem-store)
        graph (actor/build-graph {:store s :advisor (advisor/mock-advisor)})
        request {:provider-id "unknown" :op :schedule-inspection}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :done (:status result)))
    (let [ledger (store/ledger s)]
      (is (some #(= :hold (:disposition %)) ledger))
      (is (some #(= :no-provider (:rule (first (:violations (:verdict %))))) ledger)))))

(deftest run-request-escalation-flow
  (let [s (store/mem-store {:providers {"P1" {:provider-id "P1" :name "Hospital A" :verified-at "2026-01-01"}}})
        graph (actor/build-graph {:store s :advisor (advisor/mock-advisor)})
        request {:provider-id "P1" :op :flag-violation}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :interrupted (:status result)))
    (is (empty? (store/records-of s "P1")))
    ; Resume with approval
    (let [result2 (actor/approve! graph "thread-1")]
      (is (= :done (:status result2)))
      (let [records (store/records-of s "P1")]
        (is (= 1 (count records)))
        (is (= :flag-violation (:op (first records))))))))

(deftest run-request-low-confidence
  (let [s (store/mem-store {:providers {"P1" {:provider-id "P1" :name "School B" :verified-at "2026-01-01"}}})
        advisor-mock (reify advisor/Advisor
                       (-advise [_ _ _]
                         {:op :schedule-inspection :effect :propose :confidence 0.5 :rationale "low conf"}))
        graph (actor/build-graph {:store s :advisor advisor-mock})
        request {:provider-id "P1" :op :schedule-inspection}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :interrupted (:status result)))
    (let [result2 (actor/approve! graph "thread-1")]
      (is (= :done (:status result2)))
      (is (= 1 (count (store/records-of s "P1")))))))

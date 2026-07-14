(ns regulation.governor-test
  (:require [clojure.test :refer [deftest is]]
            [regulation.governor :as governor]
            [regulation.store :as store]))

(deftest check-hard-violation-unregistered-provider
  (let [s (store/mem-store)
        request {:provider-id "unknown" :op :schedule-inspection}
        proposal {:op :schedule-inspection :effect :propose :confidence 0.9}
        verdict (governor/check request {} proposal s)]
    (is (false? (:ok? verdict)))
    (is (true? (:hard? verdict)))
    (is (false? (:escalate? verdict)))
    (is (= 1 (count (:violations verdict))))
    (is (= :no-provider (-> verdict :violations first :rule)))))

(deftest check-hard-violation-non-propose-effect
  (let [s (store/mem-store {:providers {"P1" {:provider-id "P1" :name "Hospital A"}}})
        request {:provider-id "P1" :op :schedule-inspection}
        proposal {:op :schedule-inspection :effect :commit :confidence 0.9}
        verdict (governor/check request {} proposal s)]
    (is (false? (:ok? verdict)))
    (is (true? (:hard? verdict)))
    (is (false? (:escalate? verdict)))
    (is (some #(= :no-actuation (:rule %)) (:violations verdict)))))

(deftest check-hard-violation-forbidden-op
  (let [s (store/mem-store {:providers {"P1" {:provider-id "P1" :name "Hospital A"}}})
        request {:provider-id "P1"}
        proposal {:op :unknown :effect :propose :confidence 0.0}
        verdict (governor/check request {} proposal s)]
    (is (false? (:ok? verdict)))
    (is (true? (:hard? verdict)))))

(deftest check-escalation-low-confidence
  (let [s (store/mem-store {:providers {"P1" {:provider-id "P1" :name "Hospital A"}}})
        request {:provider-id "P1"}
        proposal {:op :schedule-inspection :effect :propose :confidence 0.5}
        verdict (governor/check request {} proposal s)]
    (is (false? (:ok? verdict)))
    (is (false? (:hard? verdict)))
    (is (true? (:escalate? verdict)))))

(deftest check-escalation-violation-flag
  (let [s (store/mem-store {:providers {"P1" {:provider-id "P1" :name "Hospital A"}}})
        request {:provider-id "P1"}
        proposal {:op :flag-violation :effect :propose :confidence 0.9}
        verdict (governor/check request {} proposal s)]
    (is (false? (:ok? verdict)))
    (is (false? (:hard? verdict)))
    (is (true? (:escalate? verdict)))))

(deftest check-escalation-licensing-application
  (let [s (store/mem-store {:providers {"P1" {:provider-id "P1" :name "School B"}}})
        request {:provider-id "P1"}
        proposal {:op :draft-licensing-application :effect :propose :confidence 0.9}
        verdict (governor/check request {} proposal s)]
    (is (false? (:ok? verdict)))
    (is (false? (:hard? verdict)))
    (is (true? (:escalate? verdict)))))

(deftest check-ok-schedule-inspection
  (let [s (store/mem-store {:providers {"P1" {:provider-id "P1" :name "Hospital A"}}})
        request {:provider-id "P1"}
        proposal {:op :schedule-inspection :effect :propose :confidence 0.95}
        verdict (governor/check request {} proposal s)]
    (is (true? (:ok? verdict)))
    (is (false? (:hard? verdict)))
    (is (false? (:escalate? verdict)))))

(deftest check-ok-log-compliance-report
  (let [s (store/mem-store {:providers {"P1" {:provider-id "P1" :name "Care Facility C"}}})
        request {:provider-id "P1"}
        proposal {:op :log-compliance-report :effect :propose :confidence 0.9}
        verdict (governor/check request {} proposal s)]
    (is (true? (:ok? verdict)))
    (is (false? (:hard? verdict)))
    (is (false? (:escalate? verdict)))))

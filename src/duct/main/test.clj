(ns duct.main.test
  (:require [clojure.java.io :as io]
            [duct.main.term :as term]
            [kaocha.api :as api]
            [kaocha.config :as config]
            [kaocha.output :as output]
            [kaocha.plugin :as plugin]
            [kaocha.result :as result]
            [slingshot.slingshot :refer [try+]]))

(defn- get-config-file [{:keys [test-config]
                         :or   {test-config "tests.edn"}}]
  (if (.exists (io/file test-config))
    (term/verbose (str "Loaded test configuration from: " test-config))
    (term/verbose "No test configuration found, using defaults"))
  test-config)

(defn load-config [options]
  (let [config  (-> (get-config-file options)
                    (config/load-config)
                    (config/validate!))
        plugins (plugin/load-all (:kaocha/plugins config))
        config' (plugin/with-plugins plugins
                  (plugin/run-hook :kaocha.hooks/config config))]
    (term/verbose "Loaded test plugins")
    config'))

(defn run-tests [config]
  (term/verbose "Running tests")
  (try+
    (let [totals (result/totals (:kaocha.result/tests (api/run config)))]
      (-> (+ (:kaocha.result/error totals) (:kaocha.result/fail totals))
          (min 255)))
    (catch :kaocha/early-exit {exit-code :kaocha/early-exit}
      (if (not= exit-code 0)
        (output/error "Test run exited with code " exit-code)
        (output/warn "Test run exited with code " exit-code))
      exit-code)))

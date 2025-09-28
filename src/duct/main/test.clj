(ns duct.main.test
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [duct.main.term :as term]
            [kaocha.api :as api]
            [kaocha.config :as config]
            [kaocha.output :as output]
            [kaocha.plugin :as plugin]
            [kaocha.result :as result]
            [kaocha.filter :as-alias filter]
            [slingshot.slingshot :refer [try+]]))

(defn- get-config-file [{:keys [test-config]
                         :or   {test-config "tests.edn"}}]
  (if (.exists (io/file test-config))
    (term/verbose (str "Loaded test configuration from: " test-config))
    (term/verbose "No test configuration found, using defaults"))
  test-config)

(defn- assoc-conj [m k v]
  (update m k (fnil conj []) v))

(defn- assoc-test-focus [config test-focus]
  (if (str/starts-with? test-focus "^")
    (assoc-conj config ::filter/focus-meta (read-string (subs test-focus 1)))
    (assoc-conj config ::filter/focus (read-string test-focus))))

(defn- assoc-test-skip [config test-skip]
  (if (str/starts-with? test-skip "^")
    (assoc-conj config ::filter/skip-meta (read-string (subs test-skip 1)))
    (assoc-conj config ::filter/skip (read-string test-skip))))

(defn- merge-cli-options [config {:keys [test-focus test-skip]}]
  (-> config
      (as-> c (reduce assoc-test-focus c test-focus))
      (as-> c (reduce assoc-test-skip c test-skip))))

(defn load-config [options]
  (let [config  (-> (get-config-file options)
                    (config/load-config)
                    (merge-cli-options options)
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

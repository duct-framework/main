(ns duct.main.test
  (:require [kaocha.api :as api]
            [kaocha.config :as config]
            [kaocha.output :as output]
            [kaocha.plugin :as plugin]
            [kaocha.result :as result]
            [slingshot.slingshot :refer [try+]]))

(defn load-config []
  (let [config  (-> (config/load-config "tests.edn")
                    (config/validate!))
        plugins (plugin/load-all (:kaocha/plugins config))]
    (plugin/with-plugins plugins
      (plugin/run-hook :kaocha.hooks/config config))))

(defn run-tests [config]
  (try+
    (result/totals (:kaocha.result/tests (api/run config)))
    (catch :kaocha/early-exit {exit-code :kaocha/early-exit}
      (if (not= exit-code 0)
        (output/error "Test run exited with code " exit-code)
        (output/warn "Test run exited with code " exit-code))
      exit-code)))

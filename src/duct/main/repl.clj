(ns duct.main.repl
  (:require [duct.main.config :as config]
            [integrant.core :as ig]
            [integrant.repl :as igrepl]
            [repl-balance.clojure.main :as balance]))

(defn- setup-user-ns [load-config options]
  (in-ns 'user)
  (require '[integrant.repl :refer [clear go halt prep init reset reset-all]])
  (require '[integrant.repl.state :refer [config system]])
  (igrepl/set-prep! #(-> (load-config)
                         (config/prep options)
                         (doto ig/load-namespaces)))
  (.addShutdownHook (Runtime/getRuntime)
                    (Thread. (resolve 'igrepl/halt))))

(defn create-repl [load-config options]
  (setup-user-ns load-config options)
  balance/-main)

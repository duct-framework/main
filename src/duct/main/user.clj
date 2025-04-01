(ns duct.main.user
  (:require [duct.main.config :as config]
            [integrant.core :as ig]
            [integrant.repl :as igrepl]))

(defn setup-user-ns [load-config options]
  (in-ns 'user)
  (require '[clojure.repl :refer [apropos dir doc find-doc pst source]])
  (require '[clojure.repl.deps :refer [sync-deps]])
  (require '[integrant.repl :refer [clear go halt prep init reset reset-all]])
  (require '[integrant.repl.state :refer [config system]])
  (igrepl/set-prep! #(-> (load-config)
                         (config/prep options)
                         (doto ig/load-namespaces)))
  (.addShutdownHook (Runtime/getRuntime) (Thread. igrepl/halt)))

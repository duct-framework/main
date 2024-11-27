(ns duct.main.repl
  (:require [clojure.main :as main]
            [clojure.repl :as repl]
            [duct.main.config :as config]
            [integrant.core :as ig]
            [integrant.repl :as igrepl]
            [repl-balance.core :as bal-core]
            [repl-balance.clojure.line-reader :as clj-line-reader]
            [repl-balance.clojure.main :as bal-main]
            [repl-balance.clojure.service.local :as clj-service]
            [repl-balance.jline-api :as jline]))

(defn- setup-user-ns [load-config options]
  (in-ns 'user)
  (require '[integrant.repl :refer [clear go halt prep init reset reset-all]])
  (require '[integrant.repl.state :refer [config system]])
  (igrepl/set-prep! #(-> (load-config)
                         (config/prep options)
                         (doto ig/load-namespaces)))
  (.addShutdownHook (Runtime/getRuntime)
                    (Thread. (resolve 'igrepl/halt))))

(defn- handle-sigint-form []
  `(let [thread# (Thread/currentThread)]
     (repl/set-break-handler! (fn [_signal#] (.stop thread#)))))

(defn- start-repl []
  (bal-core/ensure-terminal
   (bal-core/with-line-reader
     (clj-line-reader/create
      (clj-service/create
       (when jline/*line-reader* @jline/*line-reader*)))
     (binding [*out* (jline/safe-terminal-writer jline/*line-reader*)]
       (println "[Repl balance] Type :repl/help for online help info")
       (main/repl
        :eval   (fn [form] (eval `(do ~(handle-sigint-form) ~form)))
        :print  bal-main/syntax-highlight-prn
        :prompt (fn [])
        :read   (bal-main/create-repl-read))))))

(defn create-repl [load-config options]
  (setup-user-ns load-config options)
  start-repl)
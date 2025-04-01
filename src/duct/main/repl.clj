(ns duct.main.repl
  (:require [clojure.main :as main]
            [clojure.repl :as repl]
            [duct.main.term :as term]
            [duct.main.user :as user]
            [repl-balance.core :as bal-core]
            [repl-balance.clojure.line-reader :as clj-line-reader]
            [repl-balance.clojure.main :as bal-main]
            [repl-balance.clojure.service.local :as clj-service]
            [repl-balance.jline-api :as jline])
  (:import [org.jline.keymap KeyMap]))

(defn- handle-sigint-form []
  `(let [thread# (Thread/currentThread)]
     (repl/set-break-handler! (fn [_signal#] (.stop thread#)))))

(def ^:private duct-reset-widget
  (jline/create-widget
   (.clear jline/*buffer*)
   (jline/write (if (= 'user (ns-name *ns*))
                  "(reset)"
                  "(integrant.repl/reset)"))
   (jline/call-widget "clojure-force-accept-line")
   true))

(defn- bind-widget [widget-name service key]
  (jline/key-binding :emacs (str key) widget-name)
  (jline/apply-key-bindings!)
  (jline/set-main-key-map! (get service :key-map :emacs)))

(def help-message
  (str (term/colorize term/cyan-color "•")
       " Type :repl/help for REPL help, (go) to initiate the system and (reset)"
       "\n  to reload modified namespaces and restart the system (hotkey Alt-E)."))

(defn- start-repl []
  (bal-core/ensure-terminal
   (let [service (clj-service/create)]
     (bal-core/with-line-reader (clj-line-reader/create service)
       (doto "duct-reset-widget"
         (jline/register-widget duct-reset-widget)
         (bind-widget service (KeyMap/alt \e)))
       (binding [*out* (jline/safe-terminal-writer jline/*line-reader*)]
         (println help-message)
         (main/repl
          :eval   (fn [form] (eval `(do ~(handle-sigint-form) ~form)))
          :print  bal-main/syntax-highlight-prn
          :prompt (fn [])
          :read   (bal-main/create-repl-read)))))))

(defn create-repl [load-config options]
  (user/setup-user-ns load-config options)
  start-repl)

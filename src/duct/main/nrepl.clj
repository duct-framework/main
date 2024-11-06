(ns duct.main.nrepl
  (:require [duct.main.term :as term]
            [nrepl.cmdline :as cli]
            [nrepl.server :as nrepl]))

(defn start-nrepl [options]
  (let [server (nrepl/start-server)]
    (term/verbose (str "Started NREPL server on port " (:port server)))
    (cli/save-port-file server {})
    server))

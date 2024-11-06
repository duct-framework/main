(ns duct.main.nrepl
  (:require [nrepl.cmdline :as cli]
            [nrepl.server :as nrepl]))

(defn start-nrepl [options]
  (doto (nrepl/start-server)
    (cli/save-port-file {})))

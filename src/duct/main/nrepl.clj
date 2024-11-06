(ns duct.main.nrepl
  (:require [duct.main.term :as term]
            [nrepl.cmdline :as cli]
            [nrepl.server :as nrepl]))

(defn- nrepl-handler [{:keys [cider]}]
  (if cider
    (requiring-resolve 'cider.nrepl/cider-nrepl-handler)
    (nrepl/default-handler)))

(defn start-nrepl [options]
  (let [server (nrepl/start-server {:handler (nrepl-handler options)})]
    (term/verbose (str "Started NREPL server on port " (:port server)))
    (cli/save-port-file server {})
    server))

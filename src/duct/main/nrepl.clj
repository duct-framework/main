(ns duct.main.nrepl
  (:require [duct.main.term :as term]
            [nrepl.cmdline :as cli]
            [nrepl.server :as nrepl]))

(defn stop-nrepl-on-shutdown [server]
  (.addShutdownHook (Runtime/getRuntime)
                    (Thread. (bound-fn []
                               (term/verbose "Stopping nREPL server")
                               (nrepl/stop-server server)))))

(defn- nrepl-handler [{:keys [cider]}]
  (if cider
    (requiring-resolve 'cider.nrepl/cider-nrepl-handler)
    (nrepl/default-handler)))

(defn start-nrepl [options]
  (let [server (nrepl/start-server {:handler (nrepl-handler options)})]
    (term/verbose (str "Started nREPL server on port " (:port server)))
    (cli/save-port-file server {})
    (doto server stop-nrepl-on-shutdown)))

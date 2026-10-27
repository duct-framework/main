(ns duct.main.nrepl
  (:require [duct.main.term :as term]
            [duct.main.user :as user]
            [nrepl.cmdline :as cli]
            [nrepl.server :as nrepl]
            [nrepl.transport :as transport]))

(defn stop-nrepl-on-shutdown [server]
  (.addShutdownHook (Runtime/getRuntime)
                    (Thread. (bound-fn []
                               (term/verbose "Stopping nREPL server")
                               (nrepl/stop-server server)))))

(defn- nrepl-handler [{:keys [cider]}]
  (if cider
    (requiring-resolve 'cider.nrepl/cider-nrepl-handler)
    (nrepl/default-handler)))

(defn- bencode-transport-no-delay [^java.net.Socket socket]
  (.setTcpNoDelay socket true)
  (transport/bencode socket))

(defn start-nrepl [load-config options]
  (let [server (nrepl/start-server
                {:handler      (nrepl-handler options)
                 :transport-fn bencode-transport-no-delay})]
    (println "nREPL server started on port" (:port server))
    (cli/save-port-file server {})
    (doto server stop-nrepl-on-shutdown)
    (when-not (:main options)
      (user/setup-user-ns load-config options))))

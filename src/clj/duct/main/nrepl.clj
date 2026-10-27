(ns duct.main.nrepl
  (:require [clojure.java.io :as io]
            [duct.main.term :as term]
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

(def ^:private eval-classpath-file ".duct-eval-classpath")

(defn- parent-file [^java.io.File f]
  (.getParentFile f))

(defn- open-url ^java.net.JarURLConnection [^java.net.URL url]
  (.openConnection url))

(defn- eval-classpath []
  (when-let [url (io/resource "duct/main/nrepl/Eval.class")]
    (case (.getProtocol url)
      "jar"  (-> url open-url .getJarFileURL .toURI io/file str)
      "file" (-> (iterate parent-file (io/file (.toURI url))) (nth 4) str))))

(defn- save-eval-classpath-file []
  (if-let [classpath (eval-classpath)]
    (doto (io/file eval-classpath-file)
      (spit classpath)
      (.deleteOnExit))
    (term/printerr "Could not find duct.main.nrepl.Eval class;"
                   "if using a local or git dependency, run:"
                   "clojure -X:deps prep")))

(defn start-nrepl [load-config options]
  (let [server (nrepl/start-server
                {:handler      (nrepl-handler options)
                 :transport-fn bencode-transport-no-delay})]
    (println "nREPL server started on port" (:port server))
    (doto server stop-nrepl-on-shutdown)
    (when-not (:main options)
      (user/setup-user-ns load-config options))
    (cli/save-port-file server {})
    (save-eval-classpath-file)))

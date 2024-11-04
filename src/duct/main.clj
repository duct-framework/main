(ns duct.main
  (:require [clojure.tools.cli :as cli]
            [duct.main.term :as term]
            [integrant.core :as ig]))

(defn- parse-concatenated-keywords [s]
  (map keyword (re-seq #"(?<=:).*?(?=:|$)" s)))

(defn- find-annotated-vars [{:keys [system]}]
  (ig/load-annotations)
  (->> (keys system)
       (map (comp :duct/vars ig/describe))
       (apply merge)))

(def default-cli-options
  [[nil  "--init" "Create a blank duct.edn config file"]
   ["-p" "--profiles PROFILES" "A concatenated list of profile keys"
    :parse-fn parse-concatenated-keywords]
   ["-r" "--repl"    "Start a command-line REPL"]
   ["-s" "--show"    "Print out the expanded configuration"]
   ["-v" "--verbose" "Enable verbose logging"]
   ["-h" "--help"]])

(defn- var->cli-option [{:keys [arg doc]}]
  (when arg
    `[nil
      ~(str "--" arg " " (.toUpperCase (str arg)))
      ~@(when doc [doc])]))

(defn- cli-options [vars]
  (into default-cli-options (keep var->cli-option) (vals vars)))

(defn- print-help [{:keys [summary]}]
  (println "Usage:\n\tclojure -M:duct")
  (println (str "Options:\n" summary)))

(def ^:private blank-config-string
  "{:system {}}\n")

(defn- init-config-file [^String filename]
  (let [f (java.io.File. filename)]
    (if (.exists f)
      (do (term/printerr filename "already exists") (System/exit 1))
      (do (spit f blank-config-string) (term/printerr "Created" filename)))))

(defn- read-config [^String filename]
  (let [f (java.io.File. filename)]
    (if (.exists f) (ig/read-string (slurp f)) {})))

(defn- load-config [filename]
  (let [config (read-config filename)]
    (update config :vars #(merge (find-annotated-vars config) %))))

(defn- prep-config [config options]
  (let [pprint (requiring-resolve 'duct.pprint/pprint)
        prep   (requiring-resolve 'duct.main.config/prep)]
    (pprint (prep config options))))

(defn- init-config [config options]
  (term/with-spinner " Initiating system..."
    (let [init (requiring-resolve 'duct.main.config/init)
          halt (requiring-resolve 'duct.main.config/halt-on-shutdown)]
      (-> config
          (init options)
          (doto halt)))))

(defn- setup-user-ns [options]
  (in-ns 'user)
  (require '[integrant.repl :refer [clear go halt prep init reset reset-all]])
  (let [set-prep! (requiring-resolve 'integrant.repl/set-prep!)
        prep-repl (requiring-resolve 'duct.main.config/prep-repl)]
    (set-prep! (fn [] (prep-repl #(load-config "duct.edn") options)))
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. (resolve 'integrant.repl/halt)))))

(defn- start-repl [options]
  ((term/with-spinner " Loading REPL environment..."
     (setup-user-ns options)
     (requiring-resolve 'repl-balance.clojure.main/-main))))

(defn -main [& args]
  (let [config (load-config "duct.edn")
        opts   (cli/parse-opts args (cli-options (:vars config)))]
    (binding [term/*verbose* (-> opts :options :verbose)]
      (term/verbose "Loaded configuration from: duct.edn")
      (cond
        (-> opts :options :help)
        (print-help opts)
        (-> opts :options :init)
        (init-config-file "duct.edn")
        (-> opts :options :show)
        (prep-config config (:options opts))
        (-> opts :options :repl)
        (start-repl (:options opts))
        :else
        (init-config config (:options opts))))))

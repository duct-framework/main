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

(defn- prep-config [config vars options]
  (let [pprint (requiring-resolve 'duct.pprint/pprint)
        prep   (requiring-resolve 'duct.main.config/prep)]
    (pprint (prep config vars options))))

(defn- init-config [config vars options]
  (term/with-spinner " Initiating system..."
    (let [init (requiring-resolve 'duct.main.config/init)
          halt (requiring-resolve 'duct.main.config/halt-on-shutdown)]
      (-> config
          (init vars options)
          (doto halt)))))

(defn- start-repl []
  ((term/with-spinner " Loading REPL environment..."
     (requiring-resolve 'repl-balance.clojure.main/-main))))

(defn -main [& args]
  (let [config (read-config "duct.edn")
        vars   (merge (find-annotated-vars config) (:vars config))
        opts   (cli/parse-opts args (cli-options vars))]
    (binding [term/*verbose* (-> opts :options :verbose)]
      (term/verbose "Loaded configuration from: duct.edn")
      (cond
        (-> opts :options :help)
        (print-help opts)
        (-> opts :options :init)
        (init-config-file "duct.edn")
        (-> opts :options :show)
        (prep-config config vars (:options opts))
        (-> opts :options :repl)
        (start-repl)
        :else
        (init-config config vars (:options opts))))))

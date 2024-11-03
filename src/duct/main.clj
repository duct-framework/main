(ns duct.main
  (:require [clojure.java.io :as io]
            [clojure.tools.cli :as cli]
            [clojure.string :as str]
            [duct.main.term :as term]
            [duct.main.config :as config]
            [duct.pprint :as pp]
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
      ~(str "--" arg " " (str/upper-case arg))
      ~@(when doc [doc])]))

(defn- cli-options [vars]
  (into default-cli-options (keep var->cli-option) (vals vars)))

(defn- print-help [{:keys [summary]}]
  (println "Usage:\n\tclojure -M:duct")
  (println (str "Options:\n" summary)))

(def ^:private blank-config-string
  "{:system {}}\n")

(defn- init-config [filename]
  (let [f (io/file filename)]
    (if (.exists f)
      (do (term/printerr filename "already exists") (System/exit 1))
      (do (spit f blank-config-string) (term/printerr "Created" filename)))))

(defn- read-config [filename]
  (let [f (io/file filename)]
    (if (.exists f) (ig/read-string (slurp f)) {})))

(defn- start-repl []
  (require '[repl-balance.clojure.main])
  (eval '(repl-balance.clojure.main/-main)))

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
        (init-config "duct.edn")
        (-> opts :options :show)
        (pp/pprint (config/prep config vars (:options opts)))
        (-> opts :options :repl)
        (start-repl)
        :else
        (-> config
            (config/init vars (:options opts))
            (doto config/halt-on-shutdown))))))

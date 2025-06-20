(ns duct.main
  (:require [clojure.tools.cli :as cli]
            [clojure.java.io :as io]
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
  [["-c" "--cider" "Add CIDER middleware (used with --nrepl)"]
   [nil  "--init"  "Create a blank duct.edn config file"]
   [nil  "--init-cider" "Create a .dir-locals.el Emacs file for CIDER"]
   ["-k" "--keys KEYS" "Limit --main to start only the supplied keys"
    :parse-fn parse-concatenated-keywords]
   ["-p" "--profiles PROFILES" "A concatenated list of profile keys"
    :parse-fn parse-concatenated-keywords]
   ["-n" "--nrepl"   "Start an nREPL server"]
   ["-m" "--main"    "Start the application"]
   ["-r" "--repl"    "Start a command-line REPL"]
   ["-s" "--show"    "Print out the expanded configuration and exit"]
   ["-v" "--verbose" "Enable verbose logging"]
   ["-h" "--help"    "Print this help message and exit"]])

(defn- var->cli-option [{:keys [arg doc]}]
  (when arg
    `[nil
      ~(str "--" arg " " (.toUpperCase (str arg)))
      ~@(when doc [doc])]))

(defn- cli-options [vars]
  (into default-cli-options (keep var->cli-option) (vals vars)))

(defn- print-help [{:keys [summary]}]
  (println (str "Usage:\n\tclojure -M:duct "
                "[--init | --main | --nrepl | --repl]"))
  (println (str "Options:\n" summary)))

(def ^:private blank-config-string
  "{:system {}}\n")

(def ^:private dir-locals-file
  (delay (slurp (io/resource "duct/main/dir-locals.el"))))

(defn- output-to-file [^String content ^String filename]
  (let [f (java.io.File. filename)]
    (if (.exists f)
      (do (term/printerr filename "already exists") (System/exit 1))
      (do (spit f content) (term/printerr "Created" filename)))))

(defn- read-config [^String filename]
  (let [f (java.io.File. filename)]
    (if (.exists f) (ig/read-string (slurp f)) {})))

(defn- load-config
  ([] (load-config "duct.edn"))
  ([filename]
   (let [config (read-config filename)]
     (update config :vars #(merge (find-annotated-vars config) %)))))

(defn- disable-hashp! []
  (alter-var-root (requiring-resolve 'hashp.config/*disable-hashp*)
                  (constantly true)))

(defn- show-config [config options]
  (let [pprint (requiring-resolve 'duct.pprint/pprint)
        prep   (requiring-resolve 'duct.main.config/prep)]
    (disable-hashp!)
    (require 'hashp.preload)
    (pprint (prep config options))))

(defn- init-config [config options]
  (term/with-spinner " Initiating system"
    (let [init (requiring-resolve 'duct.main.config/init)
          halt (requiring-resolve 'duct.main.config/halt-on-shutdown)]
      (-> config
          (init options)
          (doto halt)))))

(defn- start-repl [options]
  ((term/with-spinner " Loading REPL environment"
     ((requiring-resolve 'duct.main.repl/create-repl) load-config options)))
  (System/exit 0))

(defn- start-nrepl [options]
  (term/with-spinner (if (:cider options)
                       " Starting nREPL server with CIDER"
                       " Starting nREPL server")
    ((requiring-resolve 'duct.main.nrepl/start-nrepl) load-config options)))

(defn- setup-hashp [options]
  (when (:main options) (disable-hashp!))
  (require 'hashp.preload))

(defn -main [& args]
  (let [config  (load-config)
        opts    (cli/parse-opts args (cli-options (:vars config)))
        options (:options opts)]
    (binding [term/*verbose* (-> opts :options :verbose)]
      (term/verbose "Loaded configuration from: duct.edn")
      (cond
        (:help options) (print-help opts)
        (:show options) (show-config config options)
        :else
        (do (when (and (:main options) (:repl options))
              (term/printerr "Cannot use --main and --repl options together.")
              (System/exit 1))
            (when (or (:main options) (:repl options) (:nrepl options))
              (setup-hashp options))
            (when (:init options)
              (output-to-file blank-config-string "duct.edn"))
            (when (:init-cider options)
              (output-to-file @dir-locals-file ".dir-locals.el"))
            (when (:nrepl options)
              (start-nrepl options))
            (cond
              (:main options)            (init-config config options)
              (:repl options)            (start-repl options)
              (:nrepl options)           (.join (Thread/currentThread))
              (:init options)            nil
              (:init-cider options)      nil
              :else                      (print-help opts)))))))

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
   [nil  "--init-bb" "Create a Babashka bb.edn file"]
   [nil  "--init-calva" "Create a .vscode/settings.json file for Calva"]
   [nil  "--init-cider" "Create a .dir-locals.el Emacs file for CIDER"]
   [nil  "--init-docker" "Create a Dockerfile"]
   [nil  "--init-git" "Create a .gitignore and initiate Git"]
   ["-k" "--keys KEYS" "Limit --main to start only the supplied keys"
    :parse-fn parse-concatenated-keywords]
   ["-p" "--profiles PROFILES" "A concatenated list of profile keys"
    :parse-fn parse-concatenated-keywords]
   ["-n" "--nrepl"   "Start an nREPL server"]
   ["-m" "--main"    "Start the application"]
   ["-r" "--repl"    "Start a command-line REPL"]
   ["-s" "--show"    "Print out the expanded configuration and exit"]
   ["-t" "--test"    "Run the test suite"]
   [nil  "--test-config FILE"    "Use a custom test config file"]
   [nil  "--test-focus ID" "Limit tests to run only this one"
    :parse-fn read-string]
   [nil  "--test-skip ID" "Skip this test"
    :parse-fn read-string]
   [nil  "--test-focus-meta KEY" "Limit tests to ones with this metadata"
    :parse-fn read-string]
   [nil  "--test-skip-meta KEY" "Skip tests with this metadata"
    :parse-fn read-string]
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
                "[--init | --main | --nrepl | --repl | --test]"))
  (println (str "Options:\n" summary)))

(def ^:private blank-config-string
  "{:system {}}\n")

(def ^:private dir-locals-file
  (delay (slurp (io/resource "duct/main/dir-locals.el"))))

(def ^:private vs-code-settings-file
  (delay (slurp (io/resource "duct/main/settings.json"))))

(def ^:private docker-file
  (delay (slurp (io/resource "duct/main/Dockerfile"))))

(def ^:private bb-edn-file
  (delay (slurp (io/resource "duct/main/bb.edn"))))

(def ^:private gitignore-file
  (delay (slurp (io/resource "duct/main/gitignore"))))

(defn- output-to-file [^String content filename]
  (let [f (io/file filename)]
    (if (.exists f)
      (do (term/printerr (str f) "already exists") (System/exit 1))
      (do (spit f content) (term/printerr "Created" (str f))))))

(declare read-config)

(defn- cannot-find-include-error [f]
  (ex-info (format "Unable to find include file: #duct/include \"%s\"" (str f))
           {:include f}))

(defn- include-file [^String filepath]
  (let [f (java.io.File. filepath)]
    (if (.exists f)
      (read-config f)
      (throw (cannot-find-include-error f)))))

(defn- read-config [^java.io.File f]
  (ig/read-string {:readers {'duct/include include-file
                             'duct/resource io/resource}}
                  (slurp f)))

(defn- read-main-config [^String filepath]
  (let [f (java.io.File. filepath)]
    (if (.exists f) (read-config f) {})))

(defn- load-config
  ([] (load-config "duct.edn"))
  ([filename]
   (let [config (read-main-config filename)]
     (update config :vars #(merge (find-annotated-vars config) %)))))

(defn- disable-hashp! []
  (alter-var-root (requiring-resolve 'hashp.config/*disable-hashp*)
                  (constantly true)))

(defn- show-config [config options]
  (print (term/with-spinner-temporary " Building configuration"
           (let [pprn (requiring-resolve 'duct.pprint/pprint)
                 prep (requiring-resolve 'duct.main.config/prep)]
             (disable-hashp!)
             (require 'hashp.preload)
             (with-out-str (pprn (prep config options)))))))

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

(defn- start-tests [options]
  (let [config (term/with-spinner-unbuffered " Loading test environment"
                 ((requiring-resolve 'duct.main.test/load-config) options))
        run-tests (requiring-resolve 'duct.main.test/run-tests)]
    (System/exit (run-tests config))))

(defn- setup-hashp [options]
  (when (:main options) (disable-hashp!))
  (require 'hashp.preload))

(defn- git-init []
  (let [sh     (requiring-resolve 'clojure.java.shell/sh)
        result (sh "git" "init")]
    (if (zero? (:exit result))
      (term/printerr "Created empty Git repository in .git")
      (term/printerr (:err result)))))

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
        (do (when (->> options keys (filter #{:main :repl :test}) next)
              (term/printerr "Can only use one of the following options:"
                             "--main, --repl, --test.")
              (System/exit 1))
            (when (or (:main options)
                      (:repl options)
                      (:nrepl options)
                      (:test options))
              (setup-hashp options))
            (when (:init options)
              (output-to-file blank-config-string "duct.edn"))
            (when (:init-bb options)
              (output-to-file @bb-edn-file "bb.edn"))
            (when (:init-cider options)
              (output-to-file @dir-locals-file ".dir-locals.el"))
            (when (:init-calva options)
              (let [f (io/file ".vscode" "settings.json")]
                (io/make-parents f)
                (output-to-file @vs-code-settings-file f)))
            (when (:init-docker options)
              (output-to-file @docker-file "Dockerfile"))
            (when (:init-git options)
              (when-not (.exists (io/file ".git")) (git-init))
              (output-to-file @gitignore-file ".gitignore"))
            (when (:nrepl options)
              (start-nrepl options))
            (cond
              (:main options)            (init-config config options)
              (:test options)            (start-tests options)
              (:repl options)            (start-repl options)
              (:nrepl options)           (.join (Thread/currentThread))
              (:init options)            nil
              (:init-bb options)         nil
              (:init-cider options)      nil
              (:init-calva options)      nil
              (:init-docker options)     nil
              (:init-git options)        (shutdown-agents)
              :else                      (print-help opts)))))))

(ns duct.main
  (:require [clojure.java.io :as io]
            [clojure.tools.cli :as cli]
            [clojure.string :as str]
            [duct.pprint :as pp]
            [integrant.core :as ig]))

(def ^:dynamic *verbose* false)

(defn- printerr [& args]
  (binding [*err* *out*] (apply println args)))

(defn- verbose [s]
  (when *verbose* (printerr "»" s)))

(defmulti coerce (fn [_value type] type))
(defmethod coerce :int [n _] (Long/parseLong n))
(defmethod coerce :str [s _] s)

(defn- get-env [env type]
  (some-> (System/getenv (str env))
          (cond-> type (coerce type))))

(defn- var-value [{:keys [arg env type default]} opts]
  (or (opts (keyword arg))
      (get-env env type)
      default))

(defn- resolve-vars [vars opts]
  (into {} (reduce-kv #(assoc %1 %2 (var-value %3 opts)) {} vars)))

(defn- prep [{:keys [system vars]} {:keys [profiles] :as opts}]
  (verbose "Loading keyword hierarchy and namespaces")
  (ig/load-hierarchy)
  (ig/load-namespaces system)
  (verbose "Preparing configuration")
  (let [opts (dissoc opts :profiles :help)]
    (-> system
        (ig/expand (ig/deprofile profiles))
        (ig/deprofile profiles)
        (ig/bind (resolve-vars vars opts)))))

(defn- init [config options]
  (let [prepped-config (prep config options)]
    (verbose "Initiating system")
    (ig/init prepped-config)))

(defn- halt-on-shutdown [system]
  (.addShutdownHook (Runtime/getRuntime)
                    (Thread. (bound-fn []
                               (verbose "Halting system")
                               (ig/halt! system)))))

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
   ["-s" "--show"    "Print out the expanded configuration"]
   ["-v" "--verbose" "Enable verbose logging"]
   ["-h" "--help"]])

(defn- var->cli-option [{:keys [arg doc type]}]
  (when arg
    `[nil
      ~(str "--" arg " " (str/upper-case arg))
      ~@(when doc [doc])
      ~@(when type [:parse-fn #(coerce % type)])]))

(defn- cli-options [vars]
  (into default-cli-options (keep var->cli-option) (vals vars)))

(defn- print-help [{:keys [summary]}]
  (println "Usage:\n\tclj -M:duct")
  (println (str "Options:\n" summary)))

(def ^:private blank-config-string
  "{:system {}}\n")

(defn- init-config [filename]
  (let [f (io/file filename)]
    (if (.exists f)
      (do (printerr filename "already exists") (System/exit 1))
      (do (spit f blank-config-string) (printerr "Created" filename)))))

(defn- read-config [filename]
  (let [f (io/file filename)]
    (if (.exists f) (ig/read-string (slurp f)) {})))

(defn -main [& args]
  (let [config (read-config "duct.edn")
        vars   (merge (find-annotated-vars config) (:vars config))
        opts   (cli/parse-opts args (cli-options vars))]
    (binding [*verbose* (-> opts :options :verbose)]
      (verbose "Loaded configuration from: duct.edn")
      (cond
        (-> opts :options :help)
        (print-help opts)
        (-> opts :options :init)
        (init-config "duct.edn")
        (-> opts :options :show)
        (pp/pprint (prep config (:options opts)))
        :else
        (-> config
            (init (:options opts))
            (doto halt-on-shutdown))))))

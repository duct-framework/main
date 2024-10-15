(ns duct.main
  (:require [clojure.tools.cli :as cli]
            [clojure.string :as str]
            [integrant.core :as ig]))

(defn- var-value [{:keys [env arg default]} opts]
  (or (opts (keyword arg))
      (System/getenv (str env))
      default))

(defn- resolve-vars [vars opts]
  (into {} (reduce-kv #(assoc %1 %2 (var-value %3 opts)) {} vars)))

(defn- init [{:keys [system vars]} {:keys [profiles] :as opts}]
  (let [opts (dissoc opts :profiles :help)]
    (ig/load-hierarchy)
    (-> (doto system ig/load-namespaces)
        (ig/expand (ig/deprofile profiles))
        (ig/deprofile profiles)
        (ig/bind (resolve-vars vars opts))
        (ig/init))))

(defn- halt-on-shutdown [system]
  (.addShutdownHook (Runtime/getRuntime) (Thread. #(ig/halt! system))))

(defn- parse-concatenated-keywords [s]
  (map keyword (re-seq #"(?<=:).*?(?=:|$)" s)))

(defn- find-annotated-vars [{:keys [system]}]
  (ig/load-annotations)
  (->> (keys system)
       (map (comp :duct/vars ig/describe))
       (apply merge)))

(def default-cli-options
  [["-p" "--profiles PROFILES" "A concatenated list of profile keys"
    :parse-fn parse-concatenated-keywords]
   ["-h" "--help"]])

(defn- var->cli-option [{:keys [arg doc]}]
  (when arg
    `[nil
      ~(str "--" arg " " (str/upper-case arg))
      ~@(when doc [doc])]))

(defn- cli-options [vars]
  (into default-cli-options (keep var->cli-option) (vals vars)))

(defn- print-help [{:keys [summary]}]
  (println "Usage:\n\tclj -M:duct")
  (println (str "Options:\n" summary)))

(defn -main [& args]
  (let [config (ig/read-string (slurp "duct.edn"))
        vars   (merge (find-annotated-vars config) (:vars config))
        opts   (cli/parse-opts args (cli-options vars))]
    (if (-> opts :options :help)
      (print-help opts)
      (-> config
          (init (:options opts))
          (doto halt-on-shutdown)))))

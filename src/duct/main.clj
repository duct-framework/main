(ns duct.main
  (:require [clojure.tools.cli :as cli]
            [clojure.walk :as walk]
            [integrant.core :as ig]))

(defn- resolve-param [{:keys [env default]}]
  (or (System/getenv (str env)) default))

(defn- bind-symbol [coll sym param-opts]
  (let [val (resolve-param param-opts)]
    (walk/postwalk #(if (= sym %) val %) coll)))

(defn- bind [{:keys [params] :as config} profiles]
  (-> config
      (ig/deprofile profiles)
      (update :system #(reduce-kv bind-symbol % params))))

(defn- init [{:keys [system]} profiles]
  (ig/load-hierarchy)
  (-> (doto system ig/load-namespaces)
      (ig/expand (ig/deprofile profiles))
      (ig/deprofile profiles)
      (ig/init)))

(defn- parse-concatenated-keywords [s]
  (map keyword (re-seq #"(?<=:).*?(?=:|$)" s)))

(def cli-options
  [["-p" "--profiles PROFILES" "A concatenated list of profile keys"
    :parse-fn parse-concatenated-keywords]
   ["-h" "--help"]])

(defn- print-help [{:keys [summary]}]
  (println "Usage:\n\tclj -M:duct")
  (println (str "Options:\n" summary)))

(defn -main [& args]
  (let [opts (cli/parse-opts args cli-options)]
    (if (-> opts :options :help)
      (print-help opts)
      (let [profiles (-> opts :options :profiles)]
        (-> (slurp "duct.edn")
            (ig/read-string)
            (bind profiles)
            (init profiles))))))

(ns duct.main
  (:require [clojure.tools.cli :as cli]
            [integrant.core :as ig]))

(defn- var-value [{:keys [env default]}]
  (or (System/getenv (str env)) default))

(defn- resolve-vars [params]
  (into {} (reduce-kv #(assoc %1 %2 (var-value %3)) {} params)))

(defn- init [{:keys [system vars]} profiles]
  (ig/load-hierarchy)
  (-> (doto system ig/load-namespaces)
      (ig/expand (ig/deprofile profiles))
      (ig/deprofile profiles)
      (ig/bind (resolve-vars vars))
      (ig/init)))

(defn- halt-on-shutdown [system]
  (.addShutdownHook (Runtime/getRuntime) (Thread. #(ig/halt! system))))

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
            (init profiles)
            (doto halt-on-shutdown))))))

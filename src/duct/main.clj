(ns duct.main
  (:require [clojure.tools.cli :as cli]
            [integrant.core :as ig]))

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
      (-> (slurp "duct.edn")
          (ig/read-string)
          (init (-> opts :options :profiles))))))

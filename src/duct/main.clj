(ns duct.main
  (:require [clojure.walk :as walk]
            [clojure.tools.cli :as cli]
            [integrant.core :as ig]))

(defn- default-init-fn [k]
  (let [sym (symbol k)]
    (require (symbol (namespace sym)))
    (if-let [v (find-var sym)]
      (var-get v)
      (throw (ex-info (str "No such var: " sym) {:var sym})))))

(defmethod ig/init-key :default [k v]
  ((default-init-fn k) v))

(defn- get-env-var [sym {:keys [default]}]
  (or (System/getenv (name sym)) default))

(defn- bind [coll sym opts]
  (let [val (get-env-var sym opts)]
    (walk/postwalk #(if (= sym %) val %) coll)))

(defn- bind-vars [{:keys [vars] :as config}]
  (update config :system #(reduce-kv bind % vars)))

(defn- init [{:keys [system]}]
  (-> (doto system ig/load-namespaces)
      (ig/prep)
      (ig/init)))

(def cli-options
  [["-h" "--help"]])

(defn- print-help [{:keys [summary]}]
  (println "Usage:\n\tclj -M:duct")
  (println (str "Options:\n" summary)))

(defn -main [& args]
  (let [opts (cli/parse-opts args cli-options)]
    (if (-> opts :options :help)
      (print-help opts)
      (-> (slurp "duct.edn")
          (ig/read-string)
          (bind-vars)
          (init)))))

(ns duct.main
  (:require [clojure.walk :as walk]
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

(defn -main [& _args]
  (-> (slurp "duct.edn")
      (ig/read-string)
      (bind-vars)
      (init)))

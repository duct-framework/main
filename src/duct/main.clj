(ns duct.main
  (:require [integrant.core :as ig]))

(defn- default-init-fn [k]
  (let [sym (symbol k)]
    (require (symbol (namespace sym)))
    (if-let [v (find-var sym)]
      (var-get v)
      (throw (ex-info (str "No such var: " sym) {:var sym})))))

(defmethod ig/init-key :default [k v]
  ((default-init-fn k) v))

(defn -main [& _args]
  (-> (slurp "duct.edn")
      (ig/read-string)
      (doto ig/load-namespaces)
      (ig/prep)
      (ig/init)))

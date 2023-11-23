(ns duct.main
  (:require [integrant.core :as ig]))

(defn -main [& _args]
  (-> (slurp "duct.edn")
      (ig/read-string)
      (doto ig/load-namespaces)
      (ig/prep)
      (ig/init)))

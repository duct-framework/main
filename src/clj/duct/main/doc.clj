(ns duct.main.doc
  (:require [clojure.repl :as repl]
            [clojure.string :as str]
            [integrant.core :as ig]))

(defn print-keyword-doc [kw]
  (println "-------------------------")
  (println kw)
  (if-some [ancestor-kws (-> kw ancestors reverse seq)]
    (println (str "Keyword (ancestors: " (str/join " " ancestor-kws) ")"))
    (println "Keyword"))
  (when-some [doc (-> kw ig/describe :doc)]
    (println (str "  " doc))))

(defmacro doc
  "Print documentation for a var, special form or keyword given its name.
  Keyword documentation is discovered via Integrant's annotations."
  [name]
  (if (keyword? name)
    `(print-keyword-doc ~name)
    `(repl/doc ~name)))

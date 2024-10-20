(ns duct.pprint
  (:require [clojure.pprint :as pp]
            [integrant.core :as ig]))

(defrecord TopLevelConfig [])

(defn- top-config? [x]
  (instance? TopLevelConfig x))

(defn- pprint-top-config [config]
  (pp/pprint-logical-block
   :prefix "{" :per-line-prefix " " :suffix "}"
   (loop [[[k v] & kvs] config]
     (pp/pprint-logical-block
      (pp/write-out k)
      (.write ^java.io.Writer *out* " ")
      (pp/pprint-newline :linear)
      (pp/write-out v))
     (when (seq kvs)
       (pp/pprint-newline :mandatory)
       (recur kvs)))))

(defn- config-dispatch [x]
  (cond
    (ig/ref? x)     (print "#ig/ref" (:key x))
    (ig/refset? x)  (print "#ig/refset" (:key x))
    (ig/var? x)     (print "#ig/var" (:name x))
    (ig/profile? x) (do (print "#ig/profile") (pp/simple-dispatch (into {} x)))
    (top-config? x) (pprint-top-config x)
    :else           (pp/simple-dispatch x)))

(defn pprint [config]
  (pp/with-pprint-dispatch config-dispatch
    (pp/pprint (map->TopLevelConfig config))))

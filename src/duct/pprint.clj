(ns duct.pprint
  (:require [clojure.java.classpath :as cp]
            [clojure.java.io :as io]
            [clojure.pprint :as pp]
            [clojure.string :as str]
            [integrant.core :as ig]))

(defn- parent-files [^java.io.File f]
  (take-while some? (iterate #(.getParentFile %) f)))

(defn- url->file ^java.io.File [url]
  (condp #(str/starts-with? %2 %1) url
    "jar:"  (io/file (second (re-matches #"jar:file:(.*)!.*" url)))
    "file:" (io/file (subs url 5))
    nil))

(defn- classpath-dir [^java.io.File f]
  (let [parents (parent-files f)]
    (some (fn [cp] (first (filter #(.equals cp %) parents))) (cp/classpath))))

(defn- ->path ^java.nio.file.Path [path]
  (.toPath (io/file path)))

(defn- url->resource [^java.io.File cp url]
  (condp #(str/starts-with? %2 %1) url
    "jar:"  (second (re-matches #"jar:file:.*!/(.*)" url))
    "file:" (str (.relativize (.toPath cp) (->path (subs url 5))))
    nil))

(defn- resource-path [^java.net.URL url]
  (let [url-str (str url)]
    (when-let [cp (classpath-dir (url->file url-str))]
      (url->resource cp url-str))))

(defn- url? [x]
  (instance? java.net.URL x))

(defn- pprint-url [url]
  (if-some [path (resource-path url)]
    (print "#duct/resource" (pr-str path))
    (pp/simple-dispatch url)))

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
    (url? x)        (pprint-url x)
    :else           (pp/simple-dispatch x)))

(defn pprint [config]
  (pp/with-pprint-dispatch config-dispatch
    (pp/pprint (map->TopLevelConfig config))))

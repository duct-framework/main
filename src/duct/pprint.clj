(ns duct.pprint
  (:require [clojure.java.classpath :as cp]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [puget.printer :as puget]
            [puget.color :as color]
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

(defn- url-handler [printer url]
  (if-some [path (resource-path url)]
    (puget/format-doc printer (tagged-literal 'duct/resource path))
    (puget/format-doc printer url)))

(defrecord TopLevelConfig [])

(defn- top-level-handler [printer config]
  [:group
   (color/document printer :delimiter "{")
   [:align (->> (for [[k v] config]
                  [:span
                   (puget/format-doc printer k)
                   :break
                   (puget/format-doc printer v)])
                (interpose :break))]
   (color/document printer :delimiter "}")])

(defn pprint [config]
  (puget/pprint
   (map->TopLevelConfig config)
   {:print-color true
    :print-handlers
    {integrant.core.Ref     (puget/tagged-handler 'ig/ref :key)
     integrant.core.RefSet  (puget/tagged-handler 'ig/refset :key)
     integrant.core.Var     (puget/tagged-handler 'ig/var :name)
     integrant.core.Profile (puget/tagged-handler 'ig/profile #(into {} %))
     java.net.URL           url-handler
     duct.pprint.TopLevelConfig top-level-handler}}))

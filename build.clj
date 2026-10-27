(ns build
  (:require [clojure.tools.build.api :as b]))

(def class-dir "target/classes")

(defn compile-java [_]
  (b/javac {:src-dirs   ["src/java"]
            :class-dir  class-dir
            :basis      (b/create-basis {:project "deps.edn"})
            :javac-opts ["--release" "11"]}))

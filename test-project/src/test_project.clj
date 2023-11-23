(ns test-project)

(defn hello [{:keys [name]}]
  (println "Hello" name))

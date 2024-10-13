(ns test-project
  (:require [duct.logger :as log]))

(defn hello [{:keys [logger name]}]
  (log/info logger ::greet {:name name}))

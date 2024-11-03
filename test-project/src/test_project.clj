(ns test-project
  (:require [duct.logger :as log]))

(defn hello [{:keys [logger name]}]
  (fn [_request]
    (log/info logger ::greet {:name name})
    {:status 200
     :headers {"Content-Type" "text/html; charset=UTF-8"}
     :body (str "<!DOCTYPE html>\n<html lang=\"en\">"
                "<head><title>Greet Example</title></head>"
                "<body><h1>Hello " name "</h1></body></html>")}))

(ns test-project
  (:require [next.jdbc :as jdbc]))

(def ^:const inc-counter
  "INSERT INTO counters (remote_addr) VALUES (?)
   ON CONFLICT (remote_addr) DO UPDATE SET counter = counter + 1")

(def ^:const get-counter
  "SELECT counter FROM counters WHERE remote_addr = ?")

(defn hello [{:keys [db name]}]
  (fn [{:keys [remote-addr]}]
    (let [[{counter :counters/counter}]
          (jdbc/with-transaction [tx db]
            (jdbc/execute! tx [inc-counter remote-addr])
            (jdbc/execute! tx [get-counter remote-addr]))]
      {:status 200
       :headers {"Content-Type" "text/html; charset=UTF-8"}
       :body (str "<!DOCTYPE html>\n<html lang=\"en\">"
                  "<head><title>Greet Example</title></head>"
                  "<body><h1>Hello " name " (count: " #p counter ")"
                  "</h1></body></html>")})))

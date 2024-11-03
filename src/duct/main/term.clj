(ns duct.main.term)

(def ^:dynamic *verbose* false)

(defn printerr [& args]
  (binding [*err* *out*] (apply println args)))

(defn verbose [s]
  (when *verbose* (printerr "»" s)))

(def ^:const show-cursor "\u001B[?25h")
(def ^:const hide-cursor "\u001B[?25l")

(def ^:const spinner-chars (seq "⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏"))
(def ^:const spinner-complete "✓")

(defn- spinner [message stop?]
  (binding [*err* *out*]
    (print hide-cursor)
    (loop [chars (cycle spinner-chars)]
      (when-not @stop?
        (print (str \return (first chars) message))
        (flush)
        (Thread/sleep 100)
        (recur (rest chars))))
    (println (str \return spinner-complete message show-cursor))))

(defn start-spinner [message]
  (let [stop? (atom false)]
    (.start (Thread. #(spinner message stop?)))
    #(reset! stop? true)))

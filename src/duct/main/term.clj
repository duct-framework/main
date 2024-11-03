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
  (.write *err* hide-cursor)
  (loop [chars (cycle spinner-chars)]
    (when-not @stop?
      (.write *err* (str "\r" (first chars) message))
      (.flush *err*)
      (Thread/sleep 100)
      (recur (rest chars))))
  (.write *err* (str "\r" spinner-complete message show-cursor "\n"))
  (.flush *err*))

(defn- start-spinner [message]
  (let [stop? (atom false)]
    (.start (Thread. #(spinner message stop?)))
    #(reset! stop? true)))

(defn with-spinner-fn [message f]
  (let [stop-spinner (start-spinner message)]
     (try (f) (finally (stop-spinner)))))

(defn buffer-stdout-fn [f]
  (let [buffer (java.io.ByteArrayOutputStream.)
        writer (java.io.OutputStreamWriter. buffer)]
    (try
      (with-redefs-fn {#'*out* writer} f)
      (finally
        (Thread/sleep 100)
        (print (String. (.toByteArray buffer)))))))

(defmacro with-spinner [message & body]
  `(buffer-stdout-fn (fn [] (with-spinner-fn ~message (fn [] ~@body)))))

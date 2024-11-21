(ns duct.main.term)

(def ^:const show-cursor "\u001B[?25h")
(def ^:const hide-cursor "\u001B[?25l")

(def ^:const spinner-chars (seq "⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏"))
(def ^:const spinner-complete "✓")

(def ^:const reset-color "\u001b[0m")
(def ^:const green-color "\u001b[32m")
(def ^:const cyan-color "\u001b[36m")

(defn printerr [& args]
  (binding [*err* *out*] (apply println args)))

(def color?
  (delay (not (boolean (System/getenv "NO_COLOR")))))

(defn colorize [color text]
  (if @color? (str color text reset-color) text))

(def ^:dynamic *verbose* false)

(def verbose-prefix (delay (colorize cyan-color "»")))

(defn verbose [s]
  (when *verbose* (printerr @verbose-prefix s)))

(defn- spinner [message stop?]
  (.write *err* hide-cursor)
  (loop [chars (cycle spinner-chars)]
    (when-not @stop?
      (.write *err* (str "\r" (colorize cyan-color (first chars)) message))
      (.flush *err*)
      (Thread/sleep 100)
      (recur (rest chars))))
  (.write *err* (str "\r" (colorize green-color spinner-complete)))
  (.write *err* (str message show-cursor "\n"))
  (.flush *err*))

(defn- start-spinner [message]
  (let [stop?  (atom false)
        thread (Thread. #(spinner message stop?))]
    (.start thread)
    (fn [] (reset! stop? true) (.join thread))))

(defn with-spinner-fn [message f]
  (let [stop-spinner (start-spinner message)]
     (try (f) (finally (stop-spinner)))))

(defn buffer-stdout-fn [f]
  (let [buffer (java.io.ByteArrayOutputStream.)
        writer (java.io.OutputStreamWriter. buffer)]
    (try
      (with-redefs-fn {#'*out* writer} f)
      (finally
        (.flush *out*)
        (print (String. (.toByteArray buffer)))))))

(defmacro with-spinner [message & body]
  `(if *verbose*
     (do ~@body)
     (buffer-stdout-fn (fn [] (with-spinner-fn ~message (fn [] ~@body))))))

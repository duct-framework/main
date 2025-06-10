(ns duct.main.term)

(def ^:const show-cursor "\u001B[?25h")
(def ^:const hide-cursor "\u001B[?25l")

(def ^:const spinner-chars (seq "⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏"))
(def ^:const spinner-complete "✓")
(def ^:const spinner-error "✗")

(def ^:const reset-color "\u001b[0m")
(def ^:const green-color "\u001b[32m")
(def ^:const red-color "\u001b[31m")
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

(defn- spinner [message stop]
  (.write *err* hide-cursor)
  (loop [chars (cycle spinner-chars)]
    (when-not (realized? stop)
      (.write *err* (str "\r" (colorize cyan-color (first chars)) message))
      (.flush *err*)
      (Thread/sleep 100)
      (recur (rest chars))))
  (if (= :complete @stop)
    (.write *err* (str "\r" (colorize green-color spinner-complete)))
    (.write *err* (str "\r" (colorize red-color spinner-error))))
  (.write *err* (str message show-cursor "\n"))
  (.flush *err*))

(defn- start-spinner [message]
  (let [stop   (promise)
        thread (Thread. #(spinner message stop))]
    (.start thread)
    (fn [ret] (stop ret) (.join thread))))

(defn with-spinner-fn [message f]
  (let [stop-spinner (start-spinner message)]
    (try (let [ret (f)]
           (stop-spinner :complete) ret)
         (catch Exception ex
           (stop-spinner :error)
           (throw ex)))))

(defn buffer-stdout-fn [f]
  (let [buffer (java.io.ByteArrayOutputStream.)
        writer (java.io.OutputStreamWriter. buffer)]
    (try
      (with-redefs-fn {#'*out* writer} f)
      (finally
        (print (String. (.toByteArray buffer)))
        (flush)))))

(defmacro with-spinner [message & body]
  `(if *verbose*
     (do ~@body)
     (buffer-stdout-fn (fn [] (with-spinner-fn ~message (fn [] ~@body))))))

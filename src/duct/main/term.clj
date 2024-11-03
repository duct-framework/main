(ns duct.main.term)

(def ^:dynamic *verbose* false)

(defn printerr [& args]
  (binding [*err* *out*] (apply println args)))

(defn verbose [s]
  (when *verbose* (printerr "»" s)))

((nil
  (cider-jack-in-cmd . "clojure -M:duct --nrepl --cider")
  (cider-ns-refresh-before-fn . "integrant.repl/suspend")
  (cider-ns-refresh-after-fn  . "integrant.repl/resume")))

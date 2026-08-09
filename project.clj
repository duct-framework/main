(defproject org.duct-framework/main "0.4.8"
  :description "Command-line tool for running Duct systems"
  :url "https://github.com/duct-framework/main"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.12.5"]
                 [org.clojure/tools.cli "1.4.256"]
                 [org.clojure/java.classpath "1.1.1"]
                 [mvxcvi/puget "1.3.4"]
                 [integrant "1.0.1"]
                 [integrant/repl "0.5.1"]
                 [com.bhauman/rebel-readline "0.1.11"]
                 [dev.weavejester/hashp "0.5.1"]
                 [nrepl "1.7.0"]
                 [cider/cider-nrepl "0.62.2"]
                 [lambdaisland/kaocha "1.91.1392"]])

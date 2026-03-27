(defproject org.duct-framework/main "0.4.4"
  :description "Command-line tool for running Duct systems"
  :url "https://github.com/duct-framework/main"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.12.4"]
                 [org.clojure/tools.cli "1.4.256"]
                 [org.clojure/java.classpath "1.1.1"]
                 [mvxcvi/puget "1.3.4"]
                 [integrant "1.0.1"]
                 [integrant/repl "0.5.1"]
                 [com.openvest/repl-balance "0.2.114"]
                 [dev.weavejester/hashp "0.5.1"]
                 [nrepl "1.6.0"]
                 [cider/cider-nrepl "0.58.0"]
                 [lambdaisland/kaocha "1.91.1392"]])

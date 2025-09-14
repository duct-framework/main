(defproject org.duct-framework/main "0.2.3"
  :description "Command-line tool for running Duct systems"
  :url "https://github.com/duct-framework/main"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.12.2"]
                 [org.clojure/tools.cli "1.1.230"]
                 [integrant "1.0.0"]
                 [integrant/repl "0.5.0"]
                 [com.openvest/repl-balance "0.2.114"]
                 [dev.weavejester/hashp "0.4.0"]
                 [nrepl "1.4.0"]
                 [cider/cider-nrepl "0.57.0"]
                 [lambdaisland/kaocha "1.91.1392"]])

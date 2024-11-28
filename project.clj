(defproject org.duct-framework/main "0.1.3"
  :description "Command-line tool for running Duct systems"
  :url "https://github.com/duct-framework/main"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.12.0"]
                 [org.clojure/tools.cli "1.1.230"]
                 [integrant "0.13.1"]
                 [integrant/repl "0.4.0"]
                 [com.openvest/repl-balance "0.2.114"]
                 [nrepl "1.3.0"]
                 [cider/cider-nrepl "0.50.2"]])

(ns duct.main.config
  (:require [duct.main.term :as term]
            [integrant.core :as ig]))

(defmulti coerce (fn [_value type] type))
(defmethod coerce :int [n _] (Long/parseLong n))
(defmethod coerce :str [s _] s)

(defn- get-env [{:keys [env type]}]
  (some-> (System/getenv (str env))
          (cond-> type (coerce type))))

(defn- get-opt [{:keys [arg type]} opts]
  (some-> (opts (keyword arg))
          (cond-> type (coerce type))))

(defn- assoc-var-value [opts var-map key var]
  (let [value (or (get-opt var opts) (get-env var))]
    (cond
      (some? value)            (assoc var-map key value)
      (contains? var :default) (assoc var-map key (:default var))
      :else                    var-map)))

(defn- resolve-vars [vars opts]
  (into {} (reduce-kv (partial assoc-var-value opts) {} vars)))

(defn prep [{:keys [system vars]} {:keys [profiles repl] :as opts}]
  (term/verbose "Loading keyword hierarchy and namespaces")
  (ig/load-hierarchy)
  (ig/load-namespaces system)
  (term/verbose "Preparing configuration")
  (let [opts     (dissoc opts :profiles :help :init :show :repl :main)
        profiles (conj (vec profiles) (if repl :repl :main))]
    (-> system
        (ig/expand (ig/deprofile profiles))
        (ig/deprofile profiles)
        (ig/bind (resolve-vars vars opts)))))

(defn init [config options]
  (let [prepped-config (prep config options)]
    (term/verbose "Initiating system")
    (ig/load-namespaces prepped-config)
    (ig/init prepped-config)))

(defn halt-on-shutdown [system]
  (.addShutdownHook (Runtime/getRuntime)
                    (Thread. (bound-fn []
                               (term/verbose "Halting system")
                               (ig/halt! system)))))

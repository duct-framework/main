(ns duct.main.config
  (:require [duct.main.term :as term]
            [integrant.core :as ig]))

(defmulti coerce (fn [_value type] type))
(defmethod coerce :int [n _] (Long/parseLong n))
(defmethod coerce :str [s _] s)

(defn- get-env [env type]
  (some-> (System/getenv (str env))
          (cond-> type (coerce type))))

(defn- get-opt [opts arg type]
  (some-> (opts (keyword arg))
          (cond-> type (coerce type))))

(defn- var-value [{:keys [arg env type default]} opts]
  (or (get-opt opts arg type)
      (get-env env type)
      default))

(defn- resolve-vars [vars opts]
  (into {} (reduce-kv #(assoc %1 %2 (var-value %3 opts)) {} vars)))

(defn prep [{:keys [system vars]} {:keys [profiles] :as opts}]
  (term/verbose "Loading keyword hierarchy and namespaces")
  (ig/load-hierarchy)
  (ig/load-namespaces system)
  (term/verbose "Preparing configuration")
  (let [opts     (dissoc opts :profiles :help :init :show :repl)
        profiles (conj (vec profiles) :main)]
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

(defn prep-repl [get-config {:keys [profiles] :as opts}]
  (let [{:keys [system vars]} (get-config)]
    (ig/load-hierarchy)
    (ig/load-namespaces system)
    (let [opts     (dissoc opts :profiles :help :init :show :repl)
          profiles (conj (vec profiles) :repl)]
      (-> system
          (ig/expand (ig/deprofile profiles))
          (ig/deprofile profiles)
          (ig/bind (resolve-vars vars opts))
          (doto ig/load-namespaces)))))

(ns clj-workers.core
  (:gen-class)

  (:require
    [clj-workers.handlers :refer [app-handlers]]
    ; [clj-workers.monitors :refer [start-monitors]]
    [org.httpkit.server :as server]

    [metrics.core :refer [default-registry]]
    [metrics.jvm.core :refer [instrument-jvm]]
    [metrics.reporters.console :as console]
    [metrics.ring.expose :refer [expose-metrics-as-json]]
    [metrics.ring.instrument :refer [instrument]]))

(defn start []
  (server/run-server
    (-> app-handlers
       expose-metrics-as-json)
    {:port 3002})
  (println "server running in port 3002"))

  ;TODO We need to pause there to allow stale server die
  ; (Thread/sleep (* 3 60 1000))
  ; (start-workers)
  ; (start-monitors))

(defn -main
  "Running server"
  [& args]
  (instrument-jvm default-registry)
  (start))

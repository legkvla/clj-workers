(ns clj-workers.samples.sensors
  (:require
    [clj-workers.mongo :as mongo]
    [clj-workers.workers
      :refer
      [process-item cleanup-node-simple interval-worker-iteration start-worker]]

    [environ.core :refer [env]]))

(defn coerce-sensor [sensor]
  (-> sensor
    (update :state keyword)))

(defn register-sensor [sensor])

(defn backtest-sensor [sensor])

(defn poll-sensor [{:keys [anomalies backtested-at] :as sensor}])

(defn poll-sensors-iteration []
  (interval-worker-iteration
    :sensors-poller :sensors
    :ready :processing
    poll-sensor coerce-sensor))

(defn cleanup-workers []
  (cleanup-node-simple :sensors (env :node-id)
    #{:processing} :ready))

(defn start-workers []
  (cleanup-workers)
  (mapv
    #(start-worker "sensors-poller" % poll-sensors-iteration)
    (range 1 5)))

(ns clj-workers.samples.sensors
  (:require
    [clj-workers.mongo :as mongo]
    [clj-workers.sensor-client :as client]
    [clj-workers.time :refer [time-passed?]]
    [clj-workers.workers
      :refer
      [process-item cleanup-node-simple interval-worker-iteration start-worker]]

    [environ.core :refer [env]]))

(defn coerce-sensor [sensor]
  (-> sensor
    (update :state keyword)))

(defn register-sensor [sensor]
  (mongo/insert :sensors
    (assoc sensor :state :init)))

(defn backtest-sensor [sensor]
  (client/init-backtest sensor)
  (assoc sensor
    :anomalies 0
    :backtesting-started-at (System/currentTimeMillis)
    :state :backtesting))

(defn on-sensor-backtesting-done [sensor]
  (assoc sensor
    :backtested-at (System/currentTimeMillis)
    :state :ready))

(defn poll-sensor-backtest [sensor]
  (let [{:keys [state]} (client/poll-sensor)]
    (if (= state :ready)
      (assoc sensor :state :ready)
      ;else
      (assoc sensor :state :backtesting))))

(defn poll-sensor [{:keys [anomalies backtested-at] :as sensor}]
  (let
    [
      {:keys [new-anomalies]} (client/poll-sensor sensor)
      backtesting-needed?
      (or
        (time-passed? backtested-at) (* 3600 1000)
        (> anomalies 100))]
    (cond-> sensor
      backtesting-needed?
      (assoc :state :init)

      (not backtesting-needed?)
      (assoc :anomalies (+ anomalies new-anomalies))

      (not backtesting-needed?)
      (assoc :state :ready))))

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

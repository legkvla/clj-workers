(ns clj-workers.samples.sensors
  (:require
    [clj-workers.mongo :as mongo]
    [clj-workers.samples.sensor-client :as client]
    [clj-workers.time :refer [time-passed?]]
    [clj-workers.workers :as workers]

    [environ.core :refer [env]]))

;These fns called directly

(defn find-sensor [id]
  (mongo/find-by-id :sensors id))

(defn find-sensors []
  (mongo/find-all :sensors))

(defn register-sensor [sensor]
  (mongo/insert :sensors
    (assoc sensor :state :init)))

(defn delete-sensor [sensor-id]
  (let [{:keys [state] :as sensor} (mongo/find-by-id sensor-id)]
    (case (keyword state)
      (:init :backtesting :ready)
      (when
        (workers/process-item sensor :sensors state :pending
          (fn [sensor] (assoc :state :deleted)))

        (mongo/delete-by-id :sensors sensor-id)
        true)
      (:error :deleted)
      (do
        (mongo/delete-by-id :sensors sensor-id)
        true)
      :processing false)))

;Workers

(defn coerce-sensor [sensor]
  (-> sensor
    (update :state keyword)))

(defn backtest-sensor [sensor]
  (println "Starting backend sensor: " (:name sensor))
  (client/init-backtest sensor)
  (assoc sensor
    :anomalies 0
    :backtesting-started-at (System/currentTimeMillis)
    :state :backtesting))

(defn on-sensor-backtesting-done [sensor]
  (println "Backtesting done: " (:name sensor))
  (assoc sensor
    :backtested-at (System/currentTimeMillis)
    :state :ready))

(defn poll-sensor-backtest [sensor]
  (println "Polling sensor backtest: " (:name sensor))
  (let [{:keys [state]} (client/poll-sensor)]
    (if (= state :ready)
      (on-sensor-backtesting-done sensor)
      ;else
      (assoc sensor :state :backtesting))))

(defn poll-sensor [{:keys [anomalies backtested-at] :as sensor}]
  (println "Polling sensor: " (:name sensor) ", current anomalies: " anomalies)
  (let
    [
      {:keys [new-anomalies]} (client/poll-sensor sensor)
      backtesting-needed?
      (or
        (time-passed? backtested-at (* 3600 1000))
        (> anomalies 100))]
    (cond-> sensor
      backtesting-needed?
      (assoc :state :init)

      (not backtesting-needed?)
      (assoc :anomalies (+ anomalies new-anomalies))

      (not backtesting-needed?)
      (assoc :state :ready))))

(defn init-sensors-backtest-iteration []
  (workers/state-worker-iteration
    :sensors-backtest :sensors
    :init :processing
    backtest-sensor coerce-sensor))

(defn poll-sensors-backtest-iteration []
  (workers/state-worker-iteration
    :sensors-backtest-poller :sensors
    :backtesting :processing
    poll-sensor-backtest coerce-sensor))

(defn poll-sensors-iteration []
  (workers/interval-worker-iteration
    :sensors-poller :sensors
    :ready :processing
    poll-sensor coerce-sensor))

(defn cleanup-workers []
  (workers/cleanup-node-simple :sensors (env :node-id)
    #{:processing :error} :init))

(defn start-workers []
  (cleanup-workers)
  (mapv
    #(workers/start-worker "sensors-backtest-initiator"
        % init-sensors-backtest-iteration)
    (range 1 5))
  (mapv
    #(workers/start-worker "sensors-backtesting-poller"
        % poll-sensors-backtest-iteration)
    (range 1 5))
  (mapv
    #(workers/start-worker "sensors-poller" % poll-sensors-iteration)
    (range 1 5)))

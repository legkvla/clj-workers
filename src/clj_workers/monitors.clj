(ns clj-workers.monitors
  (:require
    [clj-workers.workers :refer [monitor-jobs start-worker]]))

(defn monitor-sensors-workers []
  (start-worker :sensors-monitor 1
    #(monitor-jobs :sensors-monitor :sensors
       #{:placed :processing} (* 600 1000))))

(defn start-monitors []
  (Thread/sleep 60000)
  (monitor-sensors-workers))

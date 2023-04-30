(ns clj-workers.workers
  (:require
    [clojure.tools.logging :refer [info debug]]

    [clj-workers.airbrake :refer [make-simple-alert send-alert]]
    [clj-workers.mongo :as mongo]
    [clj-workers.wrapper :refer [wrap-job wrap-task]]

    [monger.operators :refer :all]))

(defn try-action [attempts timeout fn:action]
  (or
    (fn:action)
    (when (> attempts 0)
      (Thread/sleep timeout)
      (recur
        (dec attempts) timeout fn:action))))

(defn process-item
  [
    {:keys [id] :as item} col-name
    load-state working-state
    fn:process-item fn:coerce-item]

  (when id
    (debug "Trying to lock worker: " (:id item))
    (when-let
      [item (mongo/find-and-modify col-name item :state load-state working-state)]
      (try
        (debug "Worker locked: " (:id item))
        (mongo/save col-name
          (-> item
            fn:coerce-item
            fn:process-item
            (assoc :updated-at (System/currentTimeMillis) :errors-count 0)))
        (catch Exception ex
          (mongo/save col-name
            (assoc item
              :state (if (-> item :errors-count (or 0) (> 5)) :error load-state)
              :errors-count (-> item :errors-count (or 0) inc)
              :updated-at (System/currentTimeMillis)))
              ;TODO Introduce next-update-time
          (throw ex))))))

(defn worker-iteration
  [
    worker-id col-name
    load-state working-state
    fn:load-item fn:process-item fn:coerce-item]

  (loop []
    (debug (str "Starting iteration for " (name worker-id)))
    (if-let [item (fn:load-item)]
      (wrap-task
        worker-id item
        (fn [item]
          (when item
            (process-item item col-name
              load-state working-state
              fn:process-item fn:coerce-item)
            (debug (str (name worker-id) " iteration done for " (:id item))))))
      ;else
      (Thread/sleep 1000))
    ;to decrease load
    (Thread/sleep 100)
    (recur)))

(defn state-worker-iteration
  [
    worker-id col-name
    load-state working-state
    fn:process-item fn:coerce-item]

  (worker-iteration worker-id col-name
    load-state working-state
    #(mongo/find-first col-name :state load-state)
    fn:process-item fn:coerce-item))

(defn interval-worker-iteration
  [
    worker-id col-name
    load-state working-state
    fn:process-item fn:coerce-item]

  (worker-iteration worker-id col-name
    load-state working-state
    #(first
       (mongo/find-all col-name
        {
          :state load-state
          :updated-at
          {$lt (- (System/currentTimeMillis) (* 15 1000))}}
        {:updated-at 1}))
    fn:process-item fn:coerce-item))

(defn monitor-jobs [worker-id col-name freeze-states interval]
  (loop []
    (debug (str "Starting iteration for " (name worker-id)))
    (wrap-job worker-id
      (fn []
        (mapv
          #(send-alert
             (make-simple-alert
               :job-state-hanged-alert
               (str "Job hanged (" col-name "):" (:id %))
               worker-id))
          (mongo/find-all col-name
            {
              :state {$in freeze-states}
              :updated-at {$lt (- (System/currentTimeMillis) interval)}}
            {:updated-at 1}))))
    (Thread/sleep 600000)
    (recur)))

(defn cleanup-node-simple [col-name node-id freeze-states new-state]
  (mongo/update-all col-name
    {
      :node-id (or node-id "default-node")
      :state {$in freeze-states}}
    {
      :state new-state
      :errors-count 0}))

(defn start-worker [worker-name thread-num fn:iter]
  (debug
    (str "Starting " worker-name "-" thread-num))
  (.start
    (Thread. fn:iter (str worker-name "-" thread-num))))

(ns clj-workers.wrapper
  (:require
    [clj-workers.alerts :refer [make-alert send-alert]]
    [clojure.tools.logging :refer [error]]
    [metrics.core :refer [default-registry]]
    [metrics.timers :refer [timer] :as tmr]))

;TODO Make fn:fn last arg

(defn wrap-handler [handler-id {:keys [parameters] :as ctx} fn:handler]
  (let [op-ctx (tmr/start (timer default-registry (name handler-id)))]
    (try
      (fn:handler ctx)
      (catch Exception ex
        (error ex
          (str "Error processing " handler-id))
        (send-alert (make-alert ex handler-id nil parameters))
        (throw ex))
      (finally (tmr/stop op-ctx)))))

(defn wrap-task [task-id data fn:task]
  (let [op-ctx (tmr/start (timer default-registry (name task-id)))]
    (try
      (fn:task data)
      (catch Exception ex
        (error ex
          (str "Error processing " task-id " with data: " data))
        (send-alert (make-alert ex task-id nil data))
        nil)
      (finally (tmr/stop op-ctx)))))

(defn wrap-job [job-id fn:job]
  (let [op-ctx (tmr/start (timer default-registry (name job-id)))]
    (try
      (fn:job)
      (catch Exception ex
        (error ex (str "Error processing " job-id))
        (send-alert (make-alert ex job-id nil {}))
        nil)
      (finally (tmr/stop op-ctx)))))

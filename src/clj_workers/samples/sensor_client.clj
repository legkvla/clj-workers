(ns clj-workers.samples.sensor-client)

(defn init-backtest [sensor]
  (println "Init backtest for sensor " sensor))

(defn poll-sensor [{:keys [backtesting-started-at] :as sensor}]
  (let
    [
     ready?
     (> (- (System/currentTimeMillis) backtesting-started-at) (* 60 1000))]
    {
      :state (if ready? :ready :bt)
      :new-anomalies
      (if ready?
        (rand-int 2)
        ;else
        0)}))

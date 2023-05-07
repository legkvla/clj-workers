(ns clj-workers.time)

(defn time-passed? [ts-ms interval-ms]
  (> (- (System/currentTimeMillis) ts-ms) interval-ms))

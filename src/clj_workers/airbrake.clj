(ns clj-workers.airbrake
  (:require
    [clj-http.client :as client]
    [clj-stacktrace.core :refer [parse-exception]]
    [clj-stacktrace.repl :refer [method-str]]
    [environ.core :refer [env]]))

(defn make-error [throwable]
  (let [{:keys [class message trace-elems]} (parse-exception throwable)]
    {:type (.getName class)
     :message (if (empty? message) (.getName class) message)
     :backtrace
     (for [{:keys [file line], :as elem} trace-elems]
       {:line line :file file :function (method-str elem)})}))

(def notifier
  {
    :name "clj-workers"
    :version "1.0"
    :url "http://github.com/legkvla/clj-workers"})

(defn make-alert [throwable action {:keys [id] :as user} params]
  {
      :notifier notifier

      :errors [(make-error throwable)]
      :context
      (cond->
        {
          :environment (env :env-id)
          :language (str "Clojure-" (clojure-version))
          :action action}
          ; :component
          ; :action
          ; :url
          ; :version

        id (assoc :userId id))
      :params params})

(defn make-simple-alert [alert-type msg action]
  {
    :notifier notifier

    :errors
    [
     {
       :type alert-type
       :message msg
       :backtrace []}]
    :context
    {
      :environment (env :env-id)
      :language (str "Clojure-" (clojure-version))
      :action action}
    :params {}})


(def host "errbit.lab-twenty.com")
(def project "1")
(def url (str "https://" host "/api/v3/projects/" project "/notices"))

(defn send-alert [alert]
  (client/post
    url
    {
      :accept :json
      :content-type :json
      :form-params alert
      :query-params {:key (env :errbit-api-key)}
      :save-request? true
      :debug true
      :debug-body true
      :throw-exceptions false}))

(defn request->message
  "Maps the ring request map to the format of the airbrake params"
  [throwable {:keys [scheme server-name uri query-string params user] :as req}]
  {
    :notifier notifier

    :errors
    [(make-error throwable)]

    :context
    {
      :url (str (name scheme) "://" server-name uri)
      :userId (:id user)}

    :params
    (or params {:query-string query-string})})

;Ring middleware
(defn wrap-airbrake [handler]
  (fn
    ([request]
     (try
       (handler request)
       (catch Exception ex
         (send-alert
           (request->message ex request))
         (throw ex))))
    ([request respond raise]
     (try
       (handler request respond raise)
       (catch Exception ex
         (send-alert
           (request->message ex request))
         (throw ex))))))

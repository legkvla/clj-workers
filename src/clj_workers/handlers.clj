(ns clj-workers.handlers
  (:import
    [java.sql Timestamp])
  (:require
    [reitit.ring :as ring]
    [reitit.coercion.malli]
    [reitit.ring.malli]
    [reitit.swagger :as swagger]
    [reitit.swagger-ui :as swagger-ui]
    [reitit.ring.coercion :as coercion]
    [reitit.dev.pretty :as pretty]
    [reitit.ring.middleware.muuntaja :as muuntaja]
    [reitit.ring.middleware.exception :as exception]
    [reitit.ring.middleware.parameters :as parameters]
    ;       [reitit.ring.middleware.dev :as dev]
    ;       [reitit.ring.spec :as spec]
    ;       [spec-tools.spell :as spell]

    [muuntaja.core :as m]
    [malli.util :as mu]

    [clj-workers.samples.sensors :as sensors]
    [clj-workers.security :refer [mw:token mw:auth check-creds make-token]]
    [clj-workers.wrapper :refer [wrap-handler wrap-task]]))

(defn handler [handler-id fn:body]
  (fn [ctx] (wrap-handler handler-id ctx fn:body)))

(def handler:swagger
  ["/swagger.json"
    {:get {:no-doc true
           :swagger
           {
             :info
             {
               :title "Gateway API"
               :description "Gateway API"}
             :security [{:bearer []}]
             :securityDefinitions
             {
               :bearer
               {:type "apiKey"
                :in "header"
                :name "Authorization"}}}
           :tags [{:name "trading", :description "Trading"}]
           :handler (swagger/create-swagger-handler)}}])

(def handler:login-token
  ["/login-token"
   {
     :post
     {
       :summary "Login using static token"
       :responses
       {
         200 {:body [:map [:jwt-token string?]]}
         401 {:body [:map [:error string?]]}}
       :parameters
       {
         :body
         [:map
          [:token string?]]}
      :handler
      (handler
        :login-token
        (fn [{{{:keys [token]} :body} :parameters}]
          (if (check-creds token)
            {
              :status 200
              :body {:jwt-token (make-token :admin :admin)}}
            {
              :status 401
              :body {:error "Unauthorized"}})))}}])

(def handler:me
  ["/me"
   {
     :middleware [mw:token mw:auth]
     :get
     {
       :summary "Return identity"
       :responses
       {
         200
         {:body
           [:map
            [:identity
              [:map
                [:id string?]
                [:role string?]
                [:iat int?]
                [:exp int?]]]]}
         401 {:body [:map [:error string?]]}}
       :handler
       (handler
         :get-identity
         (fn [{:keys [identity]}]
           {
             :status 200
             :body {:identity identity}}))}}])

(def handlers:auth
  ["/auth"
   {:swagger {:tags ["auth"]}}
   handler:login-token
   handler:me])




(def reitit-config
  {
   ;;:reitit.middleware/transform dev/print-request-diffs ;; pretty diffs
   ;;:validate spec/validate ;; enable spec validation for route data
   ;;:reitit.spec/wrap spell/closed ;; strict top-level validation
   :exception pretty/exception
   :data {:coercion (reitit.coercion.malli/create
                      {;; set of keys to include in error messages
                       :error-keys #{#_:type :coercion :in :schema :value :errors :humanized #_:transformed}
                       ;; schema identity function (default: close all map schemas)
                       :compile mu/closed-schema
                       ;; strip-extra-keys (effects only predefined transformers)
                       :strip-extra-keys true
                       ;; add/set default values
                       :default-values true
                       ;; malli options
                       :options nil})
          :muuntaja m/instance
          :middleware [;; swagger feature
                       swagger/swagger-feature
                       ;; query-params & form-params
                       parameters/parameters-middleware
                       ;; content-negotiation
                       muuntaja/format-negotiate-middleware
                       ;; encoding response body
                       muuntaja/format-response-middleware
                       ;; exception handling
                       exception/exception-middleware
                       ;; decoding request body
                       muuntaja/format-request-middleware
                       ;; coercing response bodys
                       coercion/coerce-response-middleware
                       ;; coercing request parameters
                       coercion/coerce-request-middleware]}})

(def router
  (ring/router
    [handler:swagger handlers:auth]
    reitit-config))


(def default-routes
  (ring/routes
    (swagger-ui/create-swagger-ui-handler
      {:path "/"
       :config {:validatorUrl nil
                :operationsSorter "alpha"}})
    (ring/create-default-handler)))

(def app-handlers
  (ring/ring-handler router default-routes))

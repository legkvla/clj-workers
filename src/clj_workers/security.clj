(ns clj-workers.security
  (:require
    [buddy.auth :as buddy-auth]
    [buddy.auth.backends :as backends]
    [buddy.auth.middleware :refer [wrap-authentication]]
    [buddy.sign.jwt :as jwt]
    [buddy.core.keys :as keys]
    [clj-time.core :as t]
    ; [clojure.set :as set]
    [clojure.tools.logging :refer [info debug]]

    [environ.core :refer [env]]))

(defn pubkey []
  (if-let [public-key (env :public-key)] (keys/str->public-key public-key)))
(defn privkey []
  (if-let [private-key (env :private-key)] (keys/str->private-key private-key)))

(def token-expiration (t/days 365))

(def jwe-options {:alg :rsa-oaep
                  :enc :a128cbc-hs256})

(defn make-backend []
  (backends/jwe {:secret (privkey)
                 :options jwe-options}))

(defn make-token [user-id role]
  (let [now (t/now)]
    (jwt/encrypt
      {:id user-id :role role :iat now :exp (t/plus now token-expiration)}
      (pubkey) jwe-options)))

(defonce token-backend (make-backend))

(defn mw:token
  "Middleware used on routes requiring token authentication."
  [handler]
  (wrap-authentication handler token-backend))

(defn mw:auth
  "Middleware used in routes that require authentication. If request is
  not authenticated a 401 unauthorized response will be
  returned. Buddy checks if request key :identity is set to truthy
  value by any previous middleware."
  [handler]
  (fn [request]
    (if (buddy-auth/authenticated? request)
      (handler request)
      {:status 401 :body {:error "Unauthorized"}})))

(defn mw:admin
  "Middleware used on routes requiring :admin role."
  [handler]
  (fn [request]
    (if (-> request :identity :roles set (contains? "admin"))
      (handler request)
      {:status 403 :body {:error "Admin role required"}})))

(defn check-creds [token]
  (-> :admin-token env (= token)))

(ns clj-workers.mongo
  (:require
    [environ.core :refer [env]]
    [monger.core :as mg]
    [monger.collection :as mc]
    [monger.query :as mq]
    [monger.operators :refer :all])

  (:import com.mongodb.DuplicateKeyException)
  (:import org.bson.types.ObjectId)
  (:use clojure.tools.trace))


(def db-url (or (env :mongo-url) "mongodb://gateway:gateway@127.0.0.1/gateway"))
(defn init-mongo-db []
  ((mg/connect-via-uri db-url) :db))

(defonce db (delay (init-mongo-db)))

(defn mongify [obj]
  (if-let [id (:id obj)]
    (-> obj (assoc :_id (ObjectId. id)) (dissoc :id))
    obj))

(defn unmongify [result]
  (if result
    (-> result
      (assoc :id (str (:_id result)))
      (dissoc :_id))))

(defn index [coll fields unique]
  ; unique can be a boolean or a keyword
  (mc/ensure-index @db (name coll) fields
    {:unique (case unique :unique true :non-unique false unique)}))

(defn text-index [coll weights]
  ; unique can be a boolean or a keyword
  (mc/ensure-index @db (name coll)
    (->> weights
      (map (fn [[field _]] [field "text"]))
      (into (array-map)))
    {:weights weights}))

(defn find-by-id [coll id]
  (unmongify (mc/find-map-by-id @db (name coll) (ObjectId. id))))

(defn find-first [coll field value]
  (->
    (mq/with-collection @db (name coll)
      (mq/find (mongify {field value}))
      (mq/sort nil))
    first
    unmongify))

(defn find-and-modify [coll {:keys [id] :as doc} field expected-value new-value]
  (let
    [
      wr
      (mc/update @db (name coll)
        {field expected-value :_id (ObjectId. id)}
        {$set {field new-value}}
        {:multi false})]

    (when (and doc (> (.getN wr) 0) (.isUpdateOfExisting wr))
      (assoc doc field new-value))))

(defn find-all [coll where-fields sort-fields]
    (map unmongify
      (mq/with-collection @db (name coll)
        (mq/find (mongify where-fields))
        (mq/sort sort-fields))))

(defn update-all [coll where-fields update-fields]
  (mc/update @db (name coll)
    where-fields {$set update-fields} {:multi true}))

(defn find-distinct [coll field]
  (mc/distinct @db (name coll) field))

(defn save [coll rec]
    (->> rec
      ; (trace "Saving record")
      mongify
      (mc/save-and-return @db (name coll))
      unmongify))

(defn insert [coll rec]
  (try
    (->> rec
      mongify
      (mc/insert-and-return @db (name coll))
      unmongify)
    (catch DuplicateKeyException ex nil)))

(defn delete-by-id [coll id] (mc/remove-by-id @db (name coll) (ObjectId. id)))
(defn delete-all [coll fields] (mc/remove @db (name coll) fields))

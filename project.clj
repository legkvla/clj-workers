(defproject clj-workers "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :plugins
  [
    [lein-environ "1.1.0"]]
  :dependencies
  [
    [org.clojure/clojure "1.10.3"]
    [buddy "2.0.0"]
    [clj-stacktrace "0.2.8"]
    [clj-time "0.13.0"]
    ;TODO Check version
    [clj-http "2.3.0"]
    [environ "1.1.0"]
    [metosin/jsonista "0.2.6"]
    [metosin/reitit "0.5.18"]
    [http-kit "2.3.0"]

    [com.novemberain/monger "3.5.0"]

    [metrics-clojure "2.10.0"]
    [metrics-clojure-jvm "2.10.0"]
    [metrics-clojure-ring "2.10.0"]

    [org.clojure/tools.logging "1.1.0"]
    [org.clojure/tools.trace "0.7.10"]
    [org.slf4j/slf4j-log4j12 "1.7.30"]]

  :main ^:skip-aot clj-workers.core
  :min-lein-version "2.0.0"
  :target-path "target/%s"
  :profiles
  {
    :uberjar
    {
      :aot [clj-workers.core]
      :main clj-workers.core
      ; :jvm-opts ["-Dclojure.compiler.direct-linking=true"]
      :uberjar-name "workers.jar"
      :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})

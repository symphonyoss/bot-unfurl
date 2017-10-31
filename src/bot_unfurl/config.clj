;
; Copyright © 2016, 2017 Symphony Software Foundation
; SPDX-License-Identifier: Apache-2.0
;
; Licensed under the Apache License, Version 2.0 (the "License");
; you may not use this file except in compliance with the License.
; You may obtain a copy of the License at
;
;     http://www.apache.org/licenses/LICENSE-2.0
;
; Unless required by applicable law or agreed to in writing, software
; distributed under the License is distributed on an "AS IS" BASIS,
; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
; See the License for the specific language governing permissions and
; limitations under the License.
;

(ns bot-unfurl.config
  (:require [clojure.java.io       :as io]
            [clojure.string        :as s]
            [clojure.tools.logging :as log]
            [clojure.edn           :as edn]
            [clj-time.core         :as tm]
            [clj-time.coerce       :as tc]
            [aero.core             :as a]
            [mount.core            :as mnt :refer [defstate]]))

; Because java.util.logging is a hot mess
(org.slf4j.bridge.SLF4JBridgeHandler/removeHandlersForRootLogger)
(org.slf4j.bridge.SLF4JBridgeHandler/install)

(def boot-time (tm/now))

(defstate last-reload-time
          :start (tm/now))

(defmethod a/reader 'split
  [opts tag value]
  "Adds a #split reader macro to aero - see https://github.com/juxt/aero/issues/55"
  (let [[s re] value]
    (s/split s (re-pattern re))))

(defstate config
          :start (if-let [config-file (:config-file (mnt/args))]
                   (a/read-config config-file)
                   (a/read-config (io/resource "config.edn"))))

(defn- assoc-if-contains
  [m contains-key new-value]
  (if (contains? m contains-key)
    (assoc m contains-key new-value)
    m))

(defn- strip-user-info-from-blacklist-file
  [blf]
  (let [u (io/as-url blf)]
    (if (s/blank? (.getUserInfo u))
      blf
      (str (java.net.URI. (.getProtocol u)
                          "REDACTED:REDACTED"
                          (.getHost     u)
                          (.getPort     u)
                          (.getPath     u)
                          (.getQuery    u)
                          (.getRef      u))))))

(defstate safe-config
          :start (let [result config
                       result (assoc-in  result
                                         [:symphony-coords :trust-store]
                                         [(first (:trust-store (:symphony-coords result))) "REDACTED"])
                       result (assoc-in  result
                                         [:symphony-coords :user-cert]
                                         [(first (:user-cert (:symphony-coords result))) "REDACTED"])
                       result (update-in result
                                         [:jolokia-config]
                                         assoc-if-contains
                                         "password"
                                         "REDACTED")
                       result (update-in result
                                         [:jolokia-config]
                                         assoc-if-contains
                                         "keystorePassword"
                                         "REDACTED")
                       result (assoc result
                                     :blacklist-files
                                     (map strip-user-info-from-blacklist-file (:blacklist-files result)))]
                   result))

(def ^:private build-info
  (edn/read-string (slurp (io/resource "deploy-info.edn"))))

(def git-revision
  (s/trim (:hash build-info)))

(def git-url
  (str "https://github.com/symphonyoss/bot-unfurl/tree/" git-revision))

(def build-date
  (tc/from-date (:date build-info)))

(defn reload!
  "Reloads all of configuration for the bot.  This will briefly take the bot offline."
  []
  (log/debug "Reloading unfurl-bot configuration...")
  (mnt/stop)
  (mnt/start)
  (log/debug "unfurl-bot configuration reloaded."))

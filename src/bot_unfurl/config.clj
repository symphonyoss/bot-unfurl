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
            [aero.core             :as a]
            [mount.core            :as mnt :refer [defstate]]))

; Because java.util.logging is a hot mess
(org.slf4j.bridge.SLF4JBridgeHandler/removeHandlersForRootLogger)
(org.slf4j.bridge.SLF4JBridgeHandler/install)

(defmethod a/reader 'split
  [opts tag value]
  "Adds a #split reader macro to EDN"
  (let [[s re] value]
    (s/split s (re-pattern re))))

(defstate config
          :start (if-let [config-file (:config-file (mnt/args))]
                   (a/read-config config-file)
                   (a/read-config (io/resource "config.edn"))))

(defn reload
  "Reloads all of configuration for the bot.  This will briefly take the bot offline."
  []
  (log/debug "Reloading unfurl-bot configuration...")
  (mnt/stop)
  (mnt/start)
  (log/debug "unfurl-bot configuration reloaded."))

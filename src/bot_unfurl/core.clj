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

(ns bot-unfurl.core
  (:require [clojure.string        :as s]
            [clojure.tools.logging :as log]
            [mount.core            :as mnt :refer [defstate]]
            [clj-symphony.stream   :as sys]
            [clj-symphony.message  :as sym]
            [bot-unfurl.connection :as cnxn]
            [bot-unfurl.unfurl     :as uf]
            [bot-unfurl.commands   :as cmd]))

(defn- process-message!
  "Processes all messages received by the bot."
  [{:keys [message-id timestamp stream-id user-id type text]}]
  (try
    (log/debug "Received message" message-id "from user" user-id "in stream" stream-id ":" text)
    (if-not (cmd/process-admin-commands! user-id stream-id text)
      (uf/unfurl-urls-and-post-previews! message-id stream-id text))
    (catch Exception e
      (log/error e "Unexpected exception while processing message" message-id))))

(defstate unfurl-bot-listener
          :start (sym/register-listener   cnxn/symphony-connection process-message!)
          :stop  (sym/deregister-listener cnxn/symphony-connection unfurl-bot-listener))


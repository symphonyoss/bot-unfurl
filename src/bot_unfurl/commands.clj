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

(ns bot-unfurl.commands
  (:require [clojure.string        :as s]
            [clojure.pprint        :as pp]
            [clojure.tools.logging :as log]
            [clojure.java.io       :as io]
            [mount.core            :as mnt :refer [defstate]]
            [clj-time.core         :as tm]
            [clj-time.format       :as tf]
            [clj-symphony.connect  :as syc]
            [clj-symphony.user     :as syu]
            [clj-symphony.message  :as sym]
            [clj-symphony.stream   :as sys]
            [bot-unfurl.utils      :as u]
            [bot-unfurl.config     :as cfg]
            [bot-unfurl.connection :as cnxn]
            [bot-unfurl.unfurl     :as uf]))

(defn- status!
  "Provides status information about the bot."
  [stream-id _]
  (let [now           (tm/now)
        uptime        (tm/interval cfg/boot-time now)
        last-reload   (tm/interval cfg/last-reload-time now)
        free-ram      (.freeMemory  (Runtime/getRuntime))
        allocated-ram (.totalMemory (Runtime/getRuntime))
        used-ram      (- allocated-ram free-ram)
        message       (str "<messageML>"
                           "<b>Unfurl bot status as at " (u/date-as-string now) ":</b>"
                           "<p><table>"
                           "<tr><td><b>Symphony</b></td><td>Pod: " (:company cnxn/bot-user) " v" (syc/pod-version cnxn/symphony-connection) ", Agent v" (syc/agent-version cnxn/symphony-connection) "</td></tr>"
                           "<tr><td><b>Runtime</b></td><td>Clojure v" (clojure-version) " on JVM v" (System/getProperty "java.version") " (" (System/getProperty "os.arch") ")</td></tr>"
                           "<tr><td><b>Bot build</b></td><td><a href=\"" cfg/git-url "\">git revision " cfg/git-revision "</a>, built " (u/date-as-string cfg/build-date) "</td></tr>"
                           "<tr><td><b>Bot uptime</b></td><td>" (u/interval-to-string uptime) "</td></tr>"
                           "<tr><td><b>Time since last configuration reload</b></td><td>" (u/interval-to-string last-reload) "</td></tr>"
                           "<tr><td><b>Memory</b></td><td>" (u/size-to-string used-ram) " used of " (u/size-to-string allocated-ram) " allocated (" (Math/round (double (/ (* used-ram 100) allocated-ram))) "%)</td></tr>"
                           "<tr><td><b># blacklist entries</b></td><td>" (format "%,d" (count uf/blacklist)) "</td></tr>"
                           "</table></p>"
                           "</messageML>")]
    (sym/send-message! cnxn/symphony-connection stream-id message)))

(defn- config!
  "Provides the current configuration of the bot."
  [stream-id _]
  (let [now     (tm/now)
        message (str "<messageML>"
                     "<b>Unfurl bot config as at " (u/date-as-string now) ":</b>"
                     "<p><pre>" (pp/write cfg/safe-config :stream nil) "</pre></p>"
                     "</messageML>")]
    (sym/send-message! cnxn/symphony-connection stream-id message)))

(defn- build-blacklisted-row
  [[url blacklist-matches]]
  (str "<tr>"
       "<td>" url "</td>"
       "<td>" (if (empty? blacklist-matches) "No" "Yes") "</td>"
       "<td>" (s/join "<br/>" blacklist-matches) "</td>"
       "</tr>"))

(defn- blacklisted!
  "Reports whether the given URL(s) are blacklisted, and if so which blacklist entries they matched."
  [stream-id text]
  (if-let [urls (uf/find-messageml-urls text)]
    (let [blacklist-matches (into {} (map #(hash-map % (uf/blacklist-matches (str %))) urls))
          message           (str "<messageML>"
                                 "<p><table>"
                                 "<tr><th>URL</th><th>Blacklisted?</th><th>Blacklist entry matches</th></tr>"
                                 (s/join (map build-blacklisted-row blacklist-matches))
                                 "</table></p>"
                                 "</messageML>")]
      (sym/send-message! cnxn/symphony-connection stream-id message))
    (sym/send-message! cnxn/symphony-connection stream-id "<messageML>No URLs were found in your command.</messageML>")))

(defn- logs!
  "Posts the bot's current logs as a zip file."
  [stream-id _]
  (let [tmp-zip-file (java.io.File/createTempFile "bot-unfurl-logs-" ".zip")
        log-files    (cfg/log-files)]
    (u/zip-files! tmp-zip-file log-files)
    (sym/send-message! cnxn/symphony-connection stream-id
                                                (str "<messageML><b>Unfurl bot logs as at " (u/now-as-string) ":</b></messageML>")
                                                nil
                                                tmp-zip-file)
    (io/delete-file tmp-zip-file true)))

(defn- reload-config!
  "Reloads the configuration of the unfurl bot. The bot will be temporarily unavailable during this operation."
  [stream-id _]
  (sym/send-message! cnxn/symphony-connection
                     stream-id
                     (str "<messageML>Configuration reload initiated at "
                          (u/now-as-string)
                          ". This may take several minutes, during which time the bot will be unavailable.</messageML>"))
  (cfg/reload!)
  (sym/send-message! cnxn/symphony-connection
                     stream-id
                     (str "<messageML>Configuration reload completed at " (u/now-as-string) ".</messageML>")))

(defn- garbage-collect!
  "Force JVM garbage collection."
  [stream-id _]
  (sym/send-message! cnxn/symphony-connection
                     stream-id
                     (str "<messageML>Garbage collection initiated at " (u/now-as-string) ". Free memory before: " (u/size-to-string (.freeMemory (Runtime/getRuntime))) ".</messageML>"))
  (.gc (java.lang.Runtime/getRuntime))
  (sym/send-message! cnxn/symphony-connection
                     stream-id
                     (str "<messageML>Garbage collection completed at " (u/now-as-string) ". Free memory after: " (u/size-to-string (.freeMemory (Runtime/getRuntime))) ".</messageML>")))

(declare help!)

; Table of commands - each of these must be a function of 2 args (strean-id, message)
(def ^:private commands
  {
    "status"      #'status!
    "config"      #'config!
    "blacklisted" #'blacklisted!
    "logs"        #'logs!
    "reload"      #'reload-config!
    "gc"          #'garbage-collect!
    "help"        #'help!
  })

(defn- help!
  "Displays this help message."
  [stream-id _]
  (let [message (str "<messageML>"
                     "Administrative commands:"
                     "<p><table>"
                     "<tr><th>Command</th><th>Description</th></tr>"
                     (s/join (map #(str "<tr><td><b>" (key %) "</b></td><td>" (:doc (meta (val %))) "</td></tr>") (sort-by key commands)))
                     "</table></p>"
                     "</messageML>")]
    (sym/send-message! cnxn/symphony-connection stream-id message)))

(defn- process-command!
  "Looks for given command in the message text, exeucting it and returning true if it was found, false otherwise."
  [from-user-id stream-id text token]
  (if-let [command-fn (get commands token)]
    (do
      (log/debug "Admin command" token
                 "requested by"  (:email-address (syu/user cnxn/symphony-connection from-user-id))
                 "in stream"     stream-id)
      (command-fn stream-id text)
      true)
    false))

(defn process-commands!
  "Process any commands in the given message.  Returns true if a command (or help) was displayed, false otherwise."
  [from-user-id stream-id text entity-data]
  (if (and (not (s/blank? text))                                             ; Message text is not blank, AND
           (cnxn/is-admin? from-user-id)                                     ; Message came from an admin, AND
           (or (= :IM (sys/stream-type cnxn/symphony-connection stream-id))  ; Message is a 1:1 chat with the bot, OR
               (some #(= (syu/user-id cnxn/bot-user) %)                      ; Bot user is @mention'ed in the message
                     (sym/mentions {:text text :entity-data entity-data}))))
    (let [tokens (sym/tokens text)]
      (boolean (some identity (doall (map (partial process-command! from-user-id stream-id text) tokens)))))
    false))

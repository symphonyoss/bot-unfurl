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
            [mount.core            :as mnt :refer [defstate]]
            [clojure.java.jmx      :as jmx]
            [clj-time.core         :as tm]
            [clj-time.format       :as tf]
            [clj-symphony.message  :as sym]
            [clj-symphony.stream   :as sys]
            [bot-unfurl.config     :as cfg]
            [bot-unfurl.connection :as cnxn]
            [bot-unfurl.unfurl     :as uf]))

(def ^:private human-readable-formatter
   "e.g. 2017-08-17 7:31AM UTC"
   (tf/formatter "yyyy-MM-dd h:mmaa ZZZ"))

(defn- date-as-string
  "Returns the given date/time as a string, using the given formatter (defaults to human-readable-formatter if not provided)."
  ([date] (date-as-string human-readable-formatter date))
  ([formatter date]
   (tf/unparse formatter date)))

(defn- now-as-string
  "Returns the current date/time as a string, using the given formatter (defaults to human-readable-formatter if not provided)."
  ([] (now-as-string human-readable-formatter))
  ([formatter] (date-as-string (tm/now))))

(defn- interval-to-string
  [i]
  (let [im (tf/instant->map i)]
   (s/trim
     (str
       (if (pos? (:years im))
         (str (:years im) " yrs "))
       (if (or (pos? (:years im))
               (pos? (:months im)))
         (str (:months im) " mths "))
       (if (or (pos? (:years im))
               (pos? (:months im))
               (pos? (:days im)))
         (str (:days im) " days "))
       (if (or (pos? (:years im))
               (pos? (:months im))
               (pos? (:days im))
               (pos? (:hours im)))
         (str (:hours im) " hrs "))
       (if (or (pos? (:years im))
               (pos? (:months im))
               (pos? (:days im))
               (pos? (:hours im))
               (pos? (:minutes im)))
         (str (:minutes im) " mins "))
       (if (or (pos? (:years im))
               (pos? (:months im))
               (pos? (:days im))
               (pos? (:hours im))
               (pos? (:minutes im))
               (pos? (:seconds im)))
         (str (:seconds im) " secs"))))))

(def ^:private sizes ["bytes" "KB" "MB" "GB" "TB" "PB" "EB" "ZB" "YB"])

(defn- size-to-string
  [sz]
  (loop [i      0
         result (double sz)]
    (if (<= result 1024)
      (format "%.2f %s" result (nth sizes i))
      (recur (inc i)
             (/ result 1024)))))

(defn- send-status-message!
  "Provides status information about the bot."
  [stream-id _]
  (let [now         (tm/now)
        uptime      (tm/interval cfg/boot-time now)
        last-reload (tm/interval cfg/last-reload-time now)
        allocated-ram (+ (:committed (jmx/read "java.lang:type=Memory" :HeapMemoryUsage))
                         (:committed (jmx/read "java.lang:type=Memory" :NonHeapMemoryUsage)))
        message     (str "<messageML>"
                         "<b>Unfurl bot status as at " (date-as-string now) "</b>"
                         "<table>"
                         "<tr><td><b>Symphony pod version</b></td><td>" cnxn/symphony-version "</td></tr>"
                         "<tr><td><b>Java version</b></td><td>" (System/getProperty "java.version") "</td></tr>"
                         "<tr><td><b>Clojure version</b></td><td>" (clojure-version) "</td></tr>"
                         "<tr><td><b>Bot build date</b></td><td>" (date-as-string cfg/build-date) "</td></tr>"
                         "<tr><td><b>Bot build revision</b></td><td><a href=\"" cfg/git-url "\">" cfg/git-revision "</a></td></tr>"
                         "<tr><td><b>Bot uptime</b></td><td>" (interval-to-string uptime) "</td></tr>"
                         "<tr><td><b>Time since last configuration reload</b></td><td>" (interval-to-string last-reload) "</td></tr>"
                         "<tr><td><b>Memory allocated</b></td><td>" (size-to-string allocated-ram) "</td></tr>"
                         "<tr><td><b># blacklist entries</b></td><td>" (format "%,d" (count uf/blacklist)) "</td></tr>"
                         "</table>"
                         "<card accent=\"tempo-bg-color--cyan\">"
                         "<header><b>Current configuration</b></header>"
                         "<body><pre>" (pp/write cfg/safe-config :stream nil) "</pre></body></card>"
                         "</messageML>")]
    (sym/send-message! cnxn/symphony-connection stream-id message)))

(defn- reload-config!
  "Reloads the configuration of the unfurl bot. The bot will be temporarily unavailable during this operation."
  [stream-id _]
  (sym/send-message! cnxn/symphony-connection
                     stream-id
                     (str "<messageML>Configuration reload initiated at "
                          (date-as-string (tm/now))
                          ". This may take several minutes, during which time the bot will be unavailable.</messageML>"))
  (cfg/reload!)
  (sym/send-message! cnxn/symphony-connection
                     stream-id
                     (str "<messageML>Configuration reload completed at " (date-as-string (tm/now)) "</messageML>")))

(defn- garbage-collect!
  "Force JVM garbage collection."
  [stream-id _]
  (sym/send-message! cnxn/symphony-connection
                     stream-id
                     (str "<messageML>Garbage collection initiated at "
                          (date-as-string (tm/now))
                          "</messageML>"))
  (jmx/invoke "java.lang:type=Memory" :gc)
  (sym/send-message! cnxn/symphony-connection
                     stream-id
                     (str "<messageML>Garbage collection completed at " (date-as-string (tm/now)) "</messageML>")))

(declare send-help-message!)

; Table of commands - each of these must be a function of 2 args (strean-id and message text)
(def ^:private commands
  {
    "status" #'send-status-message!
    "reload" #'reload-config!
    "gc"     #'garbage-collect!
    "help"   #'send-help-message!
    "?"      #'send-help-message!
  })

(defn- send-help-message!
  "Displays this help message."
  [stream-id _]
  (let [message (str "<messageML>"
                     "Hi there!  I'm unfurl bot, and I support the following admin commands:"
                     "<table>"
                     "<tr><th>Command</th><th>Description</th></tr>"
                     (s/join (map #(str "<tr><td><b>" (key %) "</b></td><td>" (:doc (meta (val %))) "</td></tr>") (sort-by key commands)))
                     "</table>"
                     "</messageML>")]
    (sym/send-message! cnxn/symphony-connection stream-id message)))

(defn- process-command!
  "Looks for given command in the message text, exeucting it and returning true if it was found, false otherwise."
  [stream-id text [command-name command-fn]]
  (println text)  ; ####TEST!!!!
  (if (s/starts-with? text command-name)
    (do
      (log/info "Admin command" command-name "found in stream" stream-id)
      (command-fn stream-id text)
      true)
    false))

(defn process-admin-commands!
  "Finds an admin command in the given message and if found, executes it, or displays help instead."
  [from-user-id stream-id text]
  (if-let [plain-text (s/lower-case (s/trim (sym/to-plain-text text)))]
    (if (cnxn/is-admin? from-user-id)
      (if (not-any? identity (map (partial process-command! stream-id plain-text) commands))
        (send-help-message! stream-id text)))))

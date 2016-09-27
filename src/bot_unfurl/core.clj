;
; Copyright © 2016 Symphony Software Foundation
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
            [unfurl.api            :as uf]
            [clj-symphony.api      :as sy]
            [bot-unfurl.config     :as cfg]))

(def ^:private link-regex #"<a\s+href\s*=\s*\"([^\"]*)\"\s*/>")

(defstate session
          :start (sy/connect (:symphony-coords cfg/config)))

(defstate url-blacklist
          :start (:url-blacklist cfg/config))

(defn- blacklisted?
  "Returns true if the given url is blacklisted. Falsey otherwise."
  [^String url]
  (some identity (map #(.startsWith url ^String %) url-blacklist)))

(defn- unfurl-url-and-build-msg
  [^String stream-id ^String url]
  (when stream-id
    (when url
      (when-not (blacklisted? url)
        (let [unfurled    (uf/unfurl url)
              title       (:title       unfurled)
              description (:description unfurled)
              preview-url (:preview-url unfurled)]
          (str (if title       (str "<b>"        title "</b><br/>"))
               (if description (str "<i>"        description "</i><br/>"))
               (if preview-url (str "<a href=\"" preview-url "\"/><br/>"))
               ))))))
          ;####TODO: put server URL somewhere?
          ;####TODO: Detect URLs in description and link them
          ;####TODO: Detect hashtags in description and link them
          ;####TODO: Detect cashtags in description and link them

(defn- unfurl-urls-and-post-msg!
  [msg-id timestamp stream-id user-id msg-format msg-type msg]
  (try
    (log/info "Received message" msg-id "from user" user-id "in stream" stream-id ":" msg)
    (when msg
      (let [urls         (re-seq link-regex msg)
            _            (log/debug "Found" (count urls) "url(s):" (map second urls))
            message-body (s/join "<br/>" (map #(unfurl-url-and-build-msg stream-id (second %)) urls))
            message      (if (< 0 (.length message-body)) (str "<messageML>" message-body "</messageML>"))]
        (if message
          (do
            (log/debug (str "Sending message to stream " stream-id ": " message))
            (sy/send-message! session stream-id message)))))
    (catch Exception e
      (log/error e "Unexpected exception while processing message" msg-id))))

(defstate unfurl-listener
          :start (sy/register-message-listener   session unfurl-urls-and-post-msg!)
          :stop  (sy/deregister-message-listener session unfurl-listener))

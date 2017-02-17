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

(def ^:private messageml-link-regex #"<a\s+href\s*=\s*\"([^\"]+)\"\s*/>")

(defstate session
          :start (sy/connect (:symphony-coords cfg/config)))

(defstate url-blacklist
          :start (:url-blacklist cfg/config))

(defstate http-proxy
          :start (:http-proxy cfg/config))

(defn- blacklisted?
  "Returns true if the given url is blacklisted. Falsey otherwise."
  [^String url]
  (some identity (map #(.startsWith url ^String %) url-blacklist)))

(defn- detect-urls
  "Detects all URLs in the given string."
  ([s]     (detect-urls s com.linkedin.urls.detection.UrlDetectorOptions/Default))
  ([s opt] (when (and s opt)
             (.detect (com.linkedin.urls.detection.UrlDetector. s opt)))))

(defn- hyperlink-url
  "Hyperlinks the given URL in the given description, using a MessageML format <a> tag."
  [description ^com.linkedin.urls.Url url]
  (let [original-text (.getOriginalUrl url)
        corrected-url (str url)]
    (s/replace description original-text (str " <a href=\"" corrected-url "\"/>"))))

(defn- hyperlink-urls
  "Hyperlinks all URLs in the given description, using MessageML format <a> tags."
  [description]
  (loop [description description
         urls        (detect-urls description)]
    (if (empty? urls)
      description
      (recur (hyperlink-url description (first urls)) (rest urls)))))

(defn- process-description
  "Processes the description field, hyperlinking any found URLs and tagging any hash or cashtags."
  [description]
  (when description
    (s/replace
      (s/replace
        (hyperlink-urls description)
        #"\#([^\s]+)"
        " <hash tag=\"$1\"/>")
      #"\$([^\s]+)"
      " <cash tag=\"$1\"/>")))

(defn- message-ml-escape
  "Escapes the given string for MessageML."
  [^String s]
  (when s
    (org.apache.commons.lang3.StringEscapeUtils/escapeXml11 s)))

(defn- unfurl-url-and-build-msg
  "Unfurls a single URL and builds a MessageML message fragment for it."
  [^String url]
  (when url
    (try
      (if (blacklisted? url)
        (log/warn "url" url "is blacklisted - ignoring.")
        (let [unfurled    (uf/unfurl url :proxy-host (first http-proxy) :proxy-port (second http-proxy))
              url         (message-ml-escape (get unfurled :url url))
              title       (message-ml-escape (:title       unfurled))
              description (process-description (message-ml-escape (:description unfurled)))
              preview-url (message-ml-escape (:preview-url unfurled))]
          (str (if title       (str "<b>"        title "</b> - "))
               "<a href=\"" url "\"/><br/>"
               (if description (str "<i>"        description "</i><br/>"))
               (if preview-url (str "<a href=\"" preview-url "\"/><br/>")))))
      (catch Exception e
        (log/error e "Unexpected exception while unfurling url" url)))))

(defn- unfurl-urls-and-post-msg!
  "Finds all messageML links in the given message and if any are detected, unfurls their URLs and posts a single summary message back to the same stream."
  [msg-id timestamp stream-id user-id msg-format msg-type msg]
  (try
    (log/debug "Received message" msg-id "from user" user-id "in stream" stream-id ":" msg)
    (when msg
      (let [urls         (map second (re-seq messageml-link-regex msg))
            _            (log/debug "Found" (count urls) "url(s):" (s/join ", " urls))
            message-body (s/join "<br/>" (pmap unfurl-url-and-build-msg urls))
            message      (if (pos? (.length message-body)) (str "<messageML>" message-body "</messageML>"))]
        (when message
          (log/info  "Found" (count urls) "url(s) in message" msg-id "- posting unfurl message to stream" stream-id)
          (log/debug "Unfurl message:" message)
          (sy/send-message! session stream-id message))))
    (catch Exception e
      (log/error e "Unexpected exception while processing message" msg-id))))

(defstate unfurl-listener
          :start (sy/register-message-listener   session unfurl-urls-and-post-msg!)
          :stop  (sy/deregister-message-listener session unfurl-listener))

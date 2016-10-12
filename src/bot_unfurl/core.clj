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

(defstate http-proxy
          :start (:http-proxy cfg/config))

(defn- blacklisted?
  "Returns true if the given url is blacklisted. Falsey otherwise."
  [^String url]
  (some identity (map #(.startsWith url ^String %) url-blacklist)))

(defn- replace-url
  [description ^com.linkedin.urls.Url url]
  (let [original-text (.getOriginalUrl url)
        corrected-url (str url)]
    (s/replace description original-text (str "<a href=\"" corrected-url "\"/>"))))

(defn- detect-urls
  ([s]     (detect-urls s com.linkedin.urls.detection.UrlDetectorOptions/Default))
  ([s opt] (when (and s opt)
             (.detect (com.linkedin.urls.detection.UrlDetector. s opt)))))

(defn- link-urls
  [description]
  (let [urls (detect-urls description)]
    (if (pos? (count urls))
      (loop [description description
             urls        urls]
        (if (empty? urls)
          description
          (recur (replace-url description (first urls)) (rest urls))))
      description)))

(defn- process-description
  "Processes the description field, hyperlinking any found URLs and tagging any hash or cashtags."
  [description]
  (when description
    (s/replace
      (s/replace
        (link-urls description)
        #"\#([^\s]+)"
        "<hash tag=\"$2\"/>")
      #"\$([^\s]+)"
      "<cash tag=\"$2\"/>")))

(defn- process-preview-url
  [preview-url]
  (when preview-url
    (first (s/split preview-url #"\?"))))   ; Strip query strings, since Symphony API barfs when an image URI contains a query string

(defn- unfurl-url-and-build-msg
  [^String stream-id ^String url]
  (try
    (when stream-id
      (when url
        (if (blacklisted? url)
          (log/warn "url" url "is blacklisted - ignoring.")
          (let [unfurled    (uf/unfurl url :proxy-host (first http-proxy) :proxy-port (second http-proxy))
                url         (get unfurled :url url)
                title       (:title       unfurled)
                description (process-description (:description unfurled))
                preview-url (process-preview-url (:preview-url unfurled))]
            (str (if title       (str "<b>"        title "</b> - "))
                 (str "<a href=\"" url "\"/><br/>")
                 (if description (str "<i>"        description "</i><br/>"))
                 (if preview-url (str "<a href=\"" preview-url "\"/><br/>")))))))
    (catch Exception e
      (log/error e "Unexpected exception while unfurling url" url))))

(defn- unfurl-urls-and-post-msg!
  [msg-id timestamp stream-id user-id msg-format msg-type msg]
  (try
    (log/info "Received message" msg-id "from user" user-id "in stream" stream-id ":" msg)
    (when msg
      (let [urls         (re-seq link-regex msg)
            _            (log/debug "Found" (count urls) "url(s):" (s/join "," (map second urls)))
            message-body (s/join "<br/>" (pmap #(unfurl-url-and-build-msg stream-id (second %)) urls))
            message      (if (pos? (.length message-body)) (str "<messageML>" message-body "</messageML>"))]
        (when message
          (log/debug (str "Sending message to stream " stream-id ": " message))
          (sy/send-message! session stream-id message))))
    (catch Exception e
      (log/error e "Unexpected exception while processing message" msg-id))))

(defstate unfurl-listener
          :start (sy/register-message-listener   session unfurl-urls-and-post-msg!)
          :stop  (sy/deregister-message-listener session unfurl-listener))

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
  (:require [clojure.string               :as s]
            [clojure.tools.logging        :as log]
            [mount.core                   :as mnt :refer [defstate]]
            [unfurl.api                   :as uf]
            [clj-symphony.connect         :as syc]
            [clj-symphony.user            :as syu]
            [clj-symphony.message         :as sym]
            [clj-symphony.user-connection :as syuc]
            [bot-unfurl.config            :as cfg]))

(def ^:private messageml-link-regex #"<a\s+href\s*=\s*\"([^\"]+)\"\s*/?>")   ; Note: this regex matches both MessageML v1 and v2 versions of the <a> tag

(def ^:private accept-connections-interval-ms (* 1000 60 30))   ; 30 minutes

(defstate symphony-connection
          :start (let [cnxn (syc/connect (:symphony-coords cfg/config))
                       bot  (syu/user cnxn)
                       _    (log/info (str "Connected as " (:display-name bot) " (" (:email-address bot) ")"))]
                    cnxn))

(defstate blacklist
          :start (distinct (concat (:blacklist cfg/config)                                  ; Entries inline in the config file
                                   (if-let [blacklist-files (:blacklist-files cfg/config)]  ; Entries in separate text files
                                     (flatten (map #(s/split (slurp %) #"\s+") blacklist-files))))))

(defstate test!!
          :start (println blacklist))

(defstate http-proxy
          :start (:http-proxy cfg/config))

; TODO: Consider using a binary search here, to better support very large blacklists
(defn- blacklisted?
  "Returns true if the given url is blacklisted, false otherwise."
  [^String url]
  (let [url-hostname (.getHost (java.net.URL. url))]
    (some identity (map (partial s/ends-with? url-hostname) blacklist))))

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

(defn- unfurl-url-and-build-msg
  "Unfurls a single URL and builds a MessageML message fragment for it."
  [^String url]
  (when url
    (try
      (if (blacklisted? url)
        (log/warn "url" url "is blacklisted - ignoring.")
        (let [unfurled    (uf/unfurl url :proxy-host (first http-proxy) :proxy-port (second http-proxy))
              url         (sym/escape (get unfurled :url url))
              title       (sym/escape (:title       unfurled))
              description (process-description (sym/escape (:description unfurled)))
              preview-url (sym/escape (:preview-url unfurled))]
          (str "<card accent=\"tempo-bg-color--cyan\">"
               "<header>"
               (if title (str "<b>" title "</b> - ")) "<a href=\"" url "\">" url "</a>"
               (if description (str "<p class=\"tempo-text-color--secondary\">" description "</p>"))
               "</header>"
               (if preview-url (str "<body><img src=\"" preview-url "\"/></body>"))
               "</card>")))
      (catch Exception e
        (log/error e "Unexpected exception while unfurling url" url)))))

(defn- unfurl-urls-and-post-msg!
  "Finds all messageML links in the given message and if any are detected, unfurls their URLs and posts a single summary message back to the same stream."
  [{:keys [message-id timestamp stream-id user-id type text]}]
  (try
    (log/debug "Received message" message-id "from user" user-id "in stream" stream-id ":" text)
    (when text
      (let [urls         (map second (re-seq messageml-link-regex text))
            _            (log/debug "Found" (count urls) "url(s):" (s/join ", " urls))
            message-body (s/join "<br/>" (remove s/blank? (pmap unfurl-url-and-build-msg urls)))
            message      (when (pos? (count message-body))
                           (str "<messageML>" message-body "</messageML>"))]
        (when message
          (log/info  "Found" (count urls) "url(s) in message" message-id "- posting unfurl message to stream" stream-id)
          (log/debug "Unfurl message:" message)
          (sym/send-message! symphony-connection stream-id message))))
    (catch Exception e
      (log/error e "Unexpected exception while processing message" message-id))))

(defstate unfurl-listener
          :start (sym/register-listener   symphony-connection unfurl-urls-and-post-msg!)
          :stop  (sym/deregister-listener symphony-connection unfurl-listener))

(defn- accept-all-connection-requests-and-log
  "Unconditionally accepts all incoming connection requests, and logs the number accepted."
  [connection]
  (let [accept-count (syuc/accept-all-connection-requests! connection)]
    (log/info (str "Accepted " accept-count " incoming connection requests."))))

(defstate accept-connections-job
          :start (let [p (promise)]
                   (future
                     (accept-all-connection-requests-and-log symphony-connection)  ; Accept upon startup
                     (try
                       (while (= :timedout (deref p accept-connections-interval-ms :timedout))
                         (accept-all-connection-requests-and-log symphony-connection))
                       (catch InterruptedException ie)  ; Ignore interrupted exceptions
                       (catch Exception e
                         (log/warn e))))
                   #(deliver p :stopped))
          :stop  (accept-connections-job))

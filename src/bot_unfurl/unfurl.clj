;
; Copyright 2016 Fintech Open Source Foundation
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

(ns bot-unfurl.unfurl
  (:require [clojure.string               :as s]
            [clojure.tools.logging        :as log]
            [mount.core                   :as mnt :refer [defstate]]
            [unfurl.api                   :as uf]
            [clj-symphony.user            :as syu]
            [clj-symphony.message         :as sym]
            [clj-symphony.stream          :as sys]
            [clj-symphony.user-connection :as syuc]
            [bot-unfurl.config            :as cfg]
            [bot-unfurl.connection        :as cnxn]))

(def ^:private messageml-link-regex #"<a\s+href\s*=\s*\"([^\"]+)\"\s*/?>")   ; Note: this regex matches both MessageML v1 and v2 versions of the <a> tag

(defstate blacklist
          :start (let [blacklist (doall
                                   (map (partial str ".")
                                        (sort
                                          (distinct
                                            (concat (:blacklist cfg/config)                                  ; Entries inline in the config file
                                                    (if-let [blacklist-files (:blacklist-files cfg/config)]  ; Entries in separate text files
                                                      (flatten (pmap #(s/split (slurp %) #"\s+")
                                                                     blacklist-files))))))))
                       _         (log/info "Loaded blacklist with" (count blacklist) "unique entries.")]
                   blacklist))

(defstate unfurl-timeout-ms
          :start (if-let [unfurl-timeout-ms (:unfurl-timeout-ms cfg/config)]
                   unfurl-timeout-ms
                   2000))   ; If not specified, default to 2 seconds

(defstate http-proxy
          :start (:http-proxy cfg/config))

(defn- nth-blacklist-entry
  "Retrieves the nth blacklist entry, stripping the leading '.' character added by the tool during loading."
  [i]
  (subs (nth blacklist i) 1))

; TODO: Consider using a binary search here, to better support very large blacklists
; Note: in testing with the full Université Toulouse 1 Capitole blacklist (~1.2 million entries), this method is still plenty fast enough.
; It's plausible that memory issues will surface before performance ones do...
(defn blacklist-matches
  "Returns the list of blacklist matches for the given hostname, or nil if no matches."
  [^String url]
  (let [url-hostname (str "." (.getHost (java.net.URL. url)))]
    (seq (map nth-blacklist-entry
              (remove nil? (map-indexed #(if (s/ends-with? url-hostname %2) %1) blacklist))))))

(defn- detect-urls
  "Detects all URLs in the given string, or nil if none were detected."
  ([s]     (detect-urls s com.linkedin.urls.detection.UrlDetectorOptions/Default))
  ([s opt] (when (and s opt)
             (seq (.detect (com.linkedin.urls.detection.UrlDetector. s opt))))))

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
        #(str " <hash tag=\"" (sym/sanitise-tag (first %)) "\"/>"))
      #"\$([^\s]+)"
      #(str " <cash tag=\"" (sym/sanitise-tag (first %)) "\"/>"))))

(defn- unfurl-url-and-build-msg
  "Unfurls a single URL and builds a MessageML message fragment for it."
  [^String url]
  (when url
    (try
      (if-let [blacklist-matches (blacklist-matches url)]
        (log/warn url "is blacklisted - matches:" (s/join ", " blacklist-matches))
        (let [unfurled    (uf/unfurl url :timeout-ms unfurl-timeout-ms :proxy-host (first http-proxy) :proxy-port (second http-proxy))
              url         (sym/escape (get unfurled :url url))
              title       (sym/escape (:title unfurled))
              description (process-description (sym/escape (:description unfurled)))
              preview-url (sym/escape (:preview-url unfurled))]
          (if (or (not (s/blank? title))
                  (not (s/blank? description))
                  (not (s/blank? preview-url)))
            (str "<card accent=\"tempo-bg-color--cyan\">"
                 "<header>"
                 (if title (str "<b>" title "</b> - ")) "<a href=\"" url "\">" url "</a>"
                 (if description (str "<p class=\"tempo-text-color--secondary\">" description "</p>"))
                 "</header>"
                 (if preview-url (str "<body><img src=\"" preview-url "\"/></body>"))
                 "</card>"))))
      (catch Exception e
        (log/error "Unexpected exception while unfurling" url ":" (str e))))))

(defn find-messageml-urls
  "Returns unique URLs in the given MessageML message, or nil if there aren't any."
  [m]
  (seq (distinct (map second (re-seq messageml-link-regex m)))))

(defn unfurl-urls-and-post-previews!
  "Finds all messageML links in the given message and if any are detected, unfurls their URLs and posts a single summary message back to the same stream."
  [message-id stream-id text]
  (when text
    (let [urls         (find-messageml-urls text)
          _            (log/debug "Found" (count urls) "unique url(s):" (s/join ", " urls))
          message-body (s/join "<br/>" (remove s/blank? (pmap unfurl-url-and-build-msg urls)))
          message      (when-not (s/blank? message-body)
                         (str "<messageML>" message-body "</messageML>"))]
      (when message
        (log/debug "Found" (count urls) "url(s) in message" message-id "- posting unfurl message to stream" stream-id ":" message)
        (sym/send-message! cnxn/symphony-connection stream-id message)))))

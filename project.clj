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

(defproject org.symphonyoss.symphony/bot-unfurl "0.1.0-SNAPSHOT"
  :description      "A bot that looks for URIs in messages and 'unfurls' them into a new message."
  :url              "https://github.com/pmonks/bot-unfurl"
  :license          {:name "Apache License, Version 2.0"
                     :url  "http://www.apache.org/licenses/LICENSE-2.0"}
  :min-lein-version "2.5.0"
  :repositories     [["sonatype-snapshots" {:url "https://oss.sonatype.org/content/groups/public" :snapshots true}]
                     ["jitpack"            {:url "https://jitpack.io"}]]
  :dependencies     [
                      [org.clojure/clojure                 "1.8.0"]
                      [org.apache.commons/commons-lang3    "3.5"]
                      [cprop                               "0.1.9"]
                      [mount                               "0.1.10"]
                      [org.clojure/tools.cli               "0.3.5"]
                      [org.clojure/tools.logging           "0.3.1"]
                      [com.github.linkedin/URL-Detector    "2a0fede05e" :exclusions [org.apache.commons/commons-lang3]]  ; Via jitpack for now, until https://github.com/linkedin/URL-Detector/issues/2 is fixed
                      [org.clojars.pmonks/unfurl           "0.2.0"      :exclusions [org.clojure/clojure]]
                      [com.github.symphonyoss/clj-symphony "4ba9e6cf9f" :exclusions [org.clojure/clojure]]  ; Via jitpack for now

                      ; The following dependencies are inherited but have conflicting versions, so we "pin" the versions here
                      [com.fasterxml.jackson.core/jackson-databind              "2.8.4"]
                      [com.fasterxml.jackson.core/jackson-annotations           "2.8.4"]
                      [com.fasterxml.jackson.dataformat/jackson-dataformat-yaml "2.8.4"]
                      [joda-time/joda-time                                      "2.9.4"]
                    ]
  :profiles         {:dev {:dependencies [[midje      "1.8.3"]]
                           :plugins      [[lein-midje "3.2.1"]]}   ; Don't remove this or travis-ci will assplode!
                     :uberjar {:aot :all}}
  :main             bot-unfurl.main
  )

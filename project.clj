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

(defproject foundation.symphony/bot-unfurl "0.1.0-SNAPSHOT"
  :description      "A bot that looks for URIs in messages and 'unfurls' them into a new message."
  :url              "https://github.com/pmonks/clj-symphony"
  :license          {:name "Apache License, Version 2.0"
                     :url  "http://www.apache.org/licenses/LICENSE-2.0"}
  :min-lein-version "2.5.0"
  :repositories     [["sonatype-snapshots" {:url "https://oss.sonatype.org/content/groups/public" :snapshots true}]]
  :dependencies     [
                      [org.clojure/clojure              "1.8.0"]
                      [cprop                            "0.1.9"]
                      [mount                            "0.1.10"]
                      [org.clojure/tools.cli            "0.3.5"]
                      [org.clojure/tools.logging        "0.3.1"]
                      [com.linkedin/url-detector        "0.1.16"]
;                      [ch.qos.logback/logback-classic   "1.1.7"]   ; Swagger hardcodes a dependency on log4j. #fail
                      [org.clojars.pmonks/spinner       "0.3.0"]
                      [org.clojars.pmonks/unfurl        "0.1.0-SNAPSHOT" :exclusions [org.clojure/clojure]]
                      [foundation.symphony/clj-symphony "0.1.0-SNAPSHOT" :exclusions [org.clojure/clojure]]
                    ]
  :profiles         {:dev {:dependencies [[midje      "1.8.3"]]
                           :plugins      [[lein-midje "3.2.1"]]}   ; Don't remove this or travis-ci will assplode!
                     :uberjar {:aot :all}}
  :lein-release     {:deploy-via :clojars}
  :main             bot-unfurl.main
  )

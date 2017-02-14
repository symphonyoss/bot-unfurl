#!/bin/bash

mkdir -p ./uberjar/etc

cp etc/config-openshift.edn ./uberjar/etc/config.edn

mv "$(lein uberjar | sed -n 's/^Created \(.*standalone\.jar\)/\1/p')" uberjar/bot-unfurl-standalone.jar

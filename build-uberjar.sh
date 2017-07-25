#!/bin/bash

mkdir -p ./uberjar/etc

cp resources/config.edn ./uberjar/etc/config.edn
cp etc/log4j.xml.sample ./uberjar/etc/log4j.xml

mv "$(lein uberjar | sed -n 's/^Created \(.*standalone\.jar\)/\1/p')" uberjar/bot-unfurl-standalone.jar

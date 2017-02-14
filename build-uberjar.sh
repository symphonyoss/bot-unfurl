#!/bin/bash

mkdir -p ./uberjar

mv "$(lein uberjar | sed -n 's/^Created \(.*standalone\.jar\)/\1/p')" uberjar/bot-unfurl-standalone.jar

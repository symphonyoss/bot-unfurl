#!/bin/bash

mkdir -p target/uberjar

mv "$(lein uberjar | sed -n 's/^Created \(.*standalone\.jar\)/\1/p')" target/uberjar/bot-unfurl-standalone.jar

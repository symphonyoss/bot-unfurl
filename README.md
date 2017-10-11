[![Build Status](https://travis-ci.org/symphonyoss/bot-unfurl.svg?branch=master)](https://travis-ci.org/symphonyoss/bot-unfurl)
[![Open Issues](https://img.shields.io/github/issues/symphonyoss/bot-unfurl.svg)](https://github.com/symphonyoss/bot-unfurl/issues)
[![Average time to resolve an issue](http://isitmaintained.com/badge/resolution/symphonyoss/bot-unfurl.svg)](http://isitmaintained.com/project/symphonyoss/bot-unfurl "Average time to resolve an issue")
[![License](https://img.shields.io/github/license/symphonyoss/bot-unfurl.svg)](https://github.com/symphonyoss/bot-unfurl/blob/master/LICENSE)
[![Dependencies Status](http://jarkeeper.com/symphonyoss/bot-unfurl/status.svg)](http://jarkeeper.com/symphonyoss/bot-unfurl)
[![Symphony Software Foundation - Incubating](https://cdn.rawgit.com/symphonyoss/contrib-toolbox/master/images/ssf-badge-incubating.svg)](https://symphonyoss.atlassian.net/wiki/display/FM/Incubating)

![Unfurl Bot (ODP) Log Status](https://hv0dbm9dsd.execute-api.us-east-1.amazonaws.com/Prod/badge?oc_bot_name=botunfurl-dev&oc_project_name=ssf-dev)
![Unfurl Bot (Production) Log Status](https://hv0dbm9dsd.execute-api.us-east-1.amazonaws.com/Prod/badge?oc_bot_name=botunfurl-prod&oc_project_name=ssf-prod)

# unfurl bot

A small [Symphony](http://www.symphony.com/) bot that attempts to
[unfurl](https://medium.com/slack-developer-blog/everything-you-ever-wanted-to-know-about-unfurling-but-were-afraid-to-ask-or-how-to-make-your-e64b4bb9254)
URLs posted to any chat or room the bot is invited to.

"Unfurling" involves reading a variety of metadata from the given URL (title, server-preferred URL, description,
preview image, etc.), formatting those elements into a human-readable message, and posting it back to the same chat.

Here it is in action:
<p align="center">
  <img width="500px" alt="Unfurl bot example screenshot" src="https://raw.githubusercontent.com/symphonyoss/bot-unfurl/master/bot-unfurl-example.png"/>
</p>

## Installation

For now unfurl bot is available in source form only, so fire up your favourite git client and get cloning!

The bot is also running in the production Symphony network, hosted in the [Foundation's production pod](https://foundation.symphony.com).
It is not yet enabled for cross-pod communication (so other pods cannot yet see this instance), but the intention is to
enable this as soon as the project is [Activated](https://symphonyoss.atlassian.net/wiki/spaces/FM/pages/62783520/Activation).

## Configuration

unfurl bot is configured via a single, optional [EDN](https://github.com/edn-format/edn) file that's specified on the command
line.  This configuration file contains the coordinates of the various endpoints, certificates, knickknacks and gewgaws
that Symphony needs in order for a bot to connect to a pod.

It also allows one to optionally:

* specify how [Jolokia](https://jolokia.org/) is configured (used for server-side monitoring)
* specify a blacklist of host names that the bot should never, under any circumstances, unfurl
  * The blacklist can either be provided inline in the configuration file, in separate text files (blacklist entries separated by
    whitespace in each file), or both (in which case the two lists are merged and de-duped).  Each blacklist file may be hosted anywhere
    that can be read by [`clojure.core/slurp`](https://clojure.github.io/clojure/clojure.core-api.html#clojure.core/slurp).
  * Note that host names are matched "end to end", so if the blacklist contains an entry such as ".xxx", all host names
    that end with ".xxx" will be ignored (blacklisted)
* provide the coordinates of an HTTP proxy

### Configuration File Format

The configuration file is structured as follows:

```edn
{
  :symphony-coords {
    :pod-id           "<id of pod to connect to - will autopopulate whichever of the 4 URLs aren't provided. (optional - see below)>"
    :session-auth-url "<the URL of the session authentication endpoint. (optional - see below)>"
    :key-auth-url     "<The URL of the key authentication endpoint. (optional - see below)>"
    :agent-api-url    "<The URL of the agent API. (optional - see below)>"
    :pod-api-url      "<The URL of the Pod API. (optional - see below)>"
    :trust-store      ["<path to Java truststore>"        "<password of truststore>"]
    :user-cert        ["<path to bot user's certificate>" "<password of bot user's certificate>"]
    :user-email       "<bot user's email address>"
  }
  :jolokia-config {
    "host" "<jolokia-server-host>"
    "port" "<jolokia-server-port-as-a-string>"
  }
  :blacklist ["<hostname>" "<hostname>" ".xxx" "microsoft.com" ...]    ; Optional
  :blacklist-files ["/path/or/url/of/text/file.txt" "/path/or/url/of/some/other/file.txt"]    ; Optional
  :http-proxy ["<proxy-host>" <proxy-port>]    ; Optional - only needed if you use an HTTP proxy
}
```

The `:symphony-coords` are passed directly to the
[clj-symphony library's `connect` function](https://symphonyoss.github.io/clj-symphony/clj-symphony.connect.html#var-connect),
and have the same semantics as what's described there.  Typically `:pod-id` can only be used if
you're on a fully-hosted Symphony "business tier" subscription - for enterprise deployments the
agent (at least) will typically reside on-premises, with a completely different hostname than the
other system components.

The `:jolokia-config` map is passed directly to Jolokia's [`JolokiaServerConfig` constructor](https://github.com/rhuss/jolokia/blob/master/agent/jvm/src/main/java/org/jolokia/jvmagent/JolokiaServerConfig.java#L92).
See the [default Jolokia property file](https://github.com/rhuss/jolokia/blob/master/agent/jvm/src/main/resources/default-jolokia-agent.properties)
for a full list of the supported configuration options and their default values, and note that all
keys and values in this map MUST be strings (this is a Jolokia requirement).

Note: the HTTP proxy is only used for requests to the URLs that are being unfurled.  Use of an
HTTP proxy to make calls to the Symphony APIs are [not yet supported by clj-symphony](https://github.com/symphonyoss/clj-symphony/issues/1).

### Configuration File Location and Loading Mechanism

The `config.edn` file may be stored anywhere that can be read by the bot's JVM process, and is loaded using the
[aero](https://github.com/juxt/aero) library - see the [aero documentation](https://github.com/juxt/aero/blob/master/README.md)
for details on the various advanced options aero supports.

The bot ships with a [default `config.edn` file](https://github.com/symphonyoss/bot-unfurl/blob/master/resources/config.edn)
that will be read if a file is not specified on the command line.  This file delegates basically all of the settings to environment
variables, allowing the administrator to deploy and run the tool as a standalone uberjar, and configure it exclusively via
the shell.

### A Note on Security

The bot's configuration includes sensitive information (certificate locations and passwords), so please be extra careful
to secure this configuration, however you choose to manage it (in a file, environment variables, etc.).

### Logging Configuration

unfurl bot uses the [logback](https://logback.qos.ch/) library for logging, and ships with a
[reasonable default `logback.xml` file](https://github.com/symphonyoss/bot-unfurl/blob/master/resources/logback.xml).
Please review the [logback documentation](https://logback.qos.ch/manual/configuration.html#configFileProperty) if you
wish to override this default logging configuration.

## Usage

For now, you can run unfurl bot either directly or as a Docker image.

### Direct Execution

```
$ lein run -- -c <path to EDN configuration file>
```

or

```
$ lein uberjar
...
$ java -jar ./target/bot-unfurl-standalone.jar -c <path to EDN configuration file>
```

### Dockerised Execution

To build the container:

```
$ docker build -t bot-unfurl .
```

To run the container:

```
$ # Interactively:
$ docker run -v /path/to/config/directory:/etc/opt/bot-unfurl:ro bot-unfurl
$ # In the background:
$ docker run -d -v /path/to/config/directory:/etc/opt/bot-unfurl:ro bot-unfurl
```

Where `/path/to/config/directory` should be replaced with the fully qualified path of the configuration directory
_on the Docker host_.  This configuration directory must contain:

 1. the service account certificate and truststore that the bot should use
 2. a `config.edn` file (in the format described above), that points to the certificates using `/etc/opt/bot-unfurl` as the base path (that's where the configuration folder is mounted _within_ the container)

 And it may optionally also contain:
 1. a blacklist file (see above for details)
 2. a logback configuration file (typically not needed - the bot's JAR file includes a [reasonable default logback configuration](https://github.com/symphonyoss/bot-unfurl/blob/master/resources/logback.xml))

You can also use Docker Compose, by running:

```
$ docker-compose up -d
```

This assumes that the `etc` directory contains the certificate, truststore, and `config.edn` file, as described above.

## Developer Information

[GitHub project](https://github.com/symphonyoss/bot-unfurl)

[Bug Tracker](https://github.com/symphonyoss/bot-unfurl/issues)

### Branching Structure

This project has two permanent branches called `master` and `dev`.  `master` is a
[GitHub protected branch](https://help.github.com/articles/about-protected-branches/) and cannot be pushed to directly -
all pushes (from project team members) and pull requests (from the wider community) must be made against the `dev`
branch.  The project team will periodically merge outstanding changes from `dev` to `master`.

All commits to the `dev` branch automatically trigger redeployment of the instance of the bot that's configured to run against the
[Foundation's Open Developer Platform (ODP)](https://symphonyoss.atlassian.net/wiki/spaces/FM/pages/37847084/Open+Developer+Platform).
All commits to the `master` branch automatically trigger redeployment of the instance of the bot that's configured to run
against [the Foundation's production pod](https://foundation.symphony.com/).

## License

Copyright © 2016, 2017 Symphony Software Foundation

Distributed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).

SPDX-License-Identifier: [Apache-2.0](https://spdx.org/licenses/Apache-2.0)

### 3rd Party Licenses

To see the full list of licenses of all third party libraries used by this project, please run:

```shell
$ lein licenses :csv | cut -d , -f3 | sort | uniq
```

To see the dependencies and licenses in detail, run:

```shell
$ lein licenses
```


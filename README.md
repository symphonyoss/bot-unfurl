[![Build Status](https://travis-ci.org/symphonyoss/bot-unfurl.svg?branch=master)](https://travis-ci.org/symphonyoss/bot-unfurl)
[![Open Issues](https://img.shields.io/github/issues/symphonyoss/bot-unfurl.svg)](https://github.com/symphonyoss/bot-unfurl/issues)
[![Average time to resolve an issue](http://isitmaintained.com/badge/resolution/symphonyoss/bot-unfurl.svg)](http://isitmaintained.com/project/symphonyoss/bot-unfurl "Average time to resolve an issue")
[![Dependencies Status](https://versions.deps.co/symphonyoss/bot-unfurl/status.svg)](https://versions.deps.co/symphonyoss/bot-unfurl)
[![License](https://img.shields.io/github/license/symphonyoss/bot-unfurl.svg)](https://github.com/symphonyoss/bot-unfurl/blob/master/LICENSE)
[![Symphony Software Foundation - Released](https://cdn.rawgit.com/symphonyoss/contrib-toolbox/master/images/ssf-badge-released.svg)](https://symphonyoss.atlassian.net/wiki/display/FM/Released)

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

The bot is running in the production Symphony network, hosted in the [Symphony Software Foundation's production pod](https://foundation.symphony.com),
and is enabled for cross-pod communication (so users in other pods can connect to the bot and use it).  As a result,
there is no installation process, beyond requesting a connection to the bot in the Symphony directory - the bot is
running as a user called `Unfurl Bot`, in the `Symphony Software Foundation` pod.  Note that the bot will take up to 30
minutes to accept new connection requests.

And of course you can always download the bot in source form and build and run it that way, if you'd prefer.  The
remainder of this document provides instructions on how to do that.

## Configuration

unfurl bot is configured via a single, optional [EDN](https://github.com/edn-format/edn) file that may be specified on the
command line via the "-c" command line option.  You can also provide a "-h" command line option to get help on all of the
command line options the bot supports.

### Configuration File Location and Loading Mechanism

The configuration file is traditionally called `config.edn` (but may be called anything you like) and may be stored anywhere
that can be read by the bot's JVM process via standard POSIX file I/O.  It's loaded using the [aero](https://github.com/juxt/aero)
library - see the [aero documentation](https://github.com/juxt/aero/blob/master/README.md) for details on the various advanced
options aero supports.

The bot ships with a [default `config.edn` file](https://github.com/symphonyoss/bot-unfurl/blob/master/resources/config.edn)
that will be read if a config file is not specified on the command line.  This file delegates basically all configuration to
environment variables, allowing the administrator to deploy and run the bot as a standalone uberjar, and configure it exclusively
from the runtime environment.

Please refer to the [default `config.edn` file](https://github.com/symphonyoss/bot-unfurl/blob/master/resources/config.edn)
for details on using these environment variables.  Their use is not described here.

### A Note on Security

**The bot's configuration includes sensitive information (certificate locations and passwords), so please be extra careful
to secure this configuration, however you choose to manage it (in a file, environment variables, etc.).**

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
  :unfurl-timeout-ms <timeout-in-ms>    ; Optional - defaults to 2 seconds
  :http-proxy ["<proxy-host>" <proxy-port>]    ; Optional - only needed if you use an HTTP proxy
  :accept-connections-interval <minutes>    ; Optional - defaults to 30 minutes
  :admin-emails ["user1@domain.tld" "user2@domain.tld"]    ; Optional
}
```

#### :symphony-coords

The coordinates of the various endpoints, certificates, knickknacks and geegaws that the bot needs in order to connect to a
Symphony pod.  This map is passed directly to the
[clj-symphony library's `connect` function](https://symphonyoss.github.io/clj-symphony/clj-symphony.connect.html#var-connect),
and has the same semantics as what's described there.

#### :jolokia-config

The configuration of the [Jolokia](https://jolokia.org/) library, used to support server-side ops monitoring of the bot.
This map is passed directly to Jolokia's [`JolokiaServerConfig` constructor](https://github.com/rhuss/jolokia/blob/master/agent/jvm/src/main/java/org/jolokia/jvmagent/JolokiaServerConfig.java#L92).
See the [default Jolokia property file](https://github.com/rhuss/jolokia/blob/master/agent/jvm/src/main/resources/default-jolokia-agent.properties)
for a full list of the supported configuration options and their default values, and note that all
keys and values in this map MUST be strings (this is a Jolokia requirement).

#### :blacklist and :blacklist-files

These two settings define the blacklist (aka blocklist) the bot should refer to, in order to determine whether a given URL
should be ignored.  It can be provided:

* inline in the configuration file (`:blacklist`)
  * each entry in this list is added verbatim to the blacklist
* in one or more text files (`:blacklist-files`)
  * each file may be hosted anywhere that can be read by
    [`clojure.core/slurp`](https://clojure.github.io/clojure/clojure.core-api.html#clojure.core/slurp) - this includes
    both local files and remote URLs
  * each file is split into individual entries on whitespace (incl. newlines)

Regardless of how the blacklist is provided (inline, files, or both), all entries are merged and de-duped, resulting in
a single blacklist used by the bot at runtime.

Entries themselves may be a hostname, domain name, or TLD, and must not begin with a full stop (.) character.  Some examples:

| Blacklist Entry    | Description                                        |
| ------------------ | -------------------------------------------------- |
| `localhost`        | Blacklists localhost.                              |
| `xxx`              | Blacklists everything in the ".xxx" TLD.           |
| `microsoft.com`    | Blacklists every site with a ".microsoft.com" URL. |
| `drive.google.com` | Blacklists Google Drive.                           |

If you're looking for a curated public blacklist, [Université Toulouse 1 Capitole provides a comprehensive one](http://dsi.ut-capitole.fr/blacklists/index_en.php)
that's compatible with this feature (configure unfurl bot to use whichever of the various `domains` files suit your needs,
via the `:blacklist-files` setting).  Note that configuring this entire blacklist results in the bot using approximately 1GB of
memory - make sure your server and JVM are sized appropriately.

#### :unfurl-timeout-ms

The timeout, in milliseconds, for each unfurling operation.  If not specified, defaults to 2000 (2 seconds).

#### :http-proxy

The coordinates of an HTTP proxy to be used when accessing URLs that are to be unfurled.

Note that use of an HTTP proxy to make calls to the Symphony APIs are
[not yet supported by clj-symphony](https://github.com/symphonyoss/clj-symphony/issues/1).

#### :accept-connections-interval

The interval (in minutes) that the bot will use to check for and accept incoming cross-pod connection requests.  If not
specified, defaults to 30 minutes.

#### :admin-emails

A list of administrator email addresses.  These users will be able to interact with the bot via ChatOps (1:1 chats with the bot
in Symphony).  Administrators should say `help` to the bot to get a list of the available admin commands.

### Logging Configuration

unfurl bot uses the [logback](https://logback.qos.ch/) library for logging, and ships with a
[reasonable default `logback.xml` file](https://github.com/symphonyoss/bot-unfurl/blob/master/resources/logback.xml).
Please review the [logback documentation](https://logback.qos.ch/manual/configuration.html#configFileProperty) if you
wish to override this default logging configuration.

## Usage

For now, you can run unfurl bot either directly or as a Docker image.

### Direct Execution

```
$ lein git-info-edn
$ lein run -- -c <path to EDN configuration file>
```

or

```
$ lein do git-info-edn, uberjar
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
 1. blacklist files (see above for details)
 2. a logback configuration file

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

Copyright © 2016 Symphony Software Foundation

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


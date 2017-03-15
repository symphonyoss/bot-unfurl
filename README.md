[![Build Status](https://travis-ci.org/symphonyoss/bot-unfurl.svg?branch=master)](https://travis-ci.org/symphonyoss/bot-unfurl)
[![Open Issues](https://img.shields.io/github/issues/symphonyoss/bot-unfurl.svg)](https://github.com/symphonyoss/bot-unfurl/issues)
[![Average time to resolve an issue](http://isitmaintained.com/badge/resolution/symphonyoss/bot-unfurl.svg)](http://isitmaintained.com/project/symphonyoss/bot-unfurl "Average time to resolve an issue")
[![License](https://img.shields.io/github/license/symphonyoss/bot-unfurl.svg)](https://github.com/symphonyoss/bot-unfurl/blob/master/LICENSE)
[![Dependencies Status](http://jarkeeper.com/symphonyoss/bot-unfurl/status.svg)](http://jarkeeper.com/symphonyoss/bot-unfurl)
[![Symphony Software Foundation - Incubating](https://cdn.rawgit.com/symphonyoss/contrib-toolbox/master/images/ssf-badge-incubating.svg)](https://symphonyoss.atlassian.net/wiki/display/FM/Project+lifecycle)

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

## Configuration

unfurl bot is configured via a single [EDN](https://github.com/edn-format/edn) file that's specified on the command
line.  This configuration file contains the coordinates of the various endpoints, certificates, knickknacks and gewgaws
that Symphony needs in order for a bot to connect to a pod.

It also allows one to optionally:

* specify a blacklist of URL prefixes that the bot should never, under any circumstances, unfurl
* provide the coordinates of an HTTP proxy

Its structure as is follows:

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
  :url-blacklist ["<url prefix>" "<another url prefix>" "http://www.microsoft.com/" ...]   ; Optional
  :http-proxy ["<proxy-host>" <proxy-port>]   ; Optional - only needed if you use an HTTP proxy
}
```

The `:symphony-coords` described above are passed directly to the
[clj-symphony library's `connect` function](https://github.com/symphonyoss/clj-symphony#usage),
and have the same semantics as what's described there.  Typically `:pod-id` can only be used if
you're on a fully-hosted Symphony "business tier" subscription - for enterprise deployments the
agent (at least) will typically reside on-premises, with a completely different hostname than the
other system components.

Note: the HTTP proxy is only used for requests to the URLs that are being unfurled.  Use of an
HTTP proxy to make calls to Symphony are [not yet supported by clj-symphony](https://github.com/symphonyoss/clj-symphony/issues/1).

[A sample `config.edn` file is provided in the `etc` directory.](https://github.com/symphonyoss/bot-unfurl/blob/master/etc/config.edn.sample)

Finally, this file is loaded using the [aero](https://github.com/juxt/aero) library, which offers quite a bit
of flexibility around how values are specified in the file (they can be read from environment variables,
for example).  See the [aero documentation](https://github.com/juxt/aero/blob/master/README.md) for details.

## Usage

For now, you can run unfurl bot either standalone or as a Docker image.

### Standalone

```
$ lein run -- -c <path to EDN configuration file>
```

### Docker

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
 3. a log4j v1.x configuration file (either `log4j.xml` or `log4j.properties`) - technically this is optional but the bot will generate a lot of logging output without it.  It is recommended that the log4j files be written to the console (STDOUT), so that docker's `logs` command can be utilised.

[A sample `log4j.xml` file is provided in the `etc` directory.](https://github.com/symphonyoss/bot-unfurl/blob/master/etc/log4j.xml.sample)

You can also use Docker Compose, by running:

```
$ docker-compose up -d
```

This assumes that the `etc` directory contains the certificate, truststore, `config.edn` file, and log4j configuration file, as described above.

## Developer Information

[GitHub project](https://github.com/symphonyoss/bot-unfurl)

[Bug Tracker](https://github.com/symphonyoss/bot-unfurl/issues)

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


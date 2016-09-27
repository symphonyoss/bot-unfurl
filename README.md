[![Open Issues](https://img.shields.io/github/issues/pmonks/bot-unfurl.svg)](https://github.com/pmonks/bot-unfurl/issues)
[![License](https://img.shields.io/github/license/pmonks/bot-unfurl.svg)](https://github.com/pmonks/bot-unfurl/blob/master/LICENSE)
[![Dependencies Status](http://jarkeeper.com/pmonks/bot-unfurl/status.svg)](http://jarkeeper.com/pmonks/bot-unfurl)

# unfurl bot

A small [Symphony](http://www.symphony.com/) bot that attempts to
[unfurl](https://medium.com/slack-developer-blog/everything-you-ever-wanted-to-know-about-unfurling-but-were-afraid-to-ask-or-how-to-make-your-e64b4bb9254)
URLs posted to any chat or room the bot is invited to.

## Installation

For now unfurl bot is available in source form only, so fire up your favourite git client and get cloning!

## Configuration

unfurl bot is configured via a single [EDN](https://github.com/edn-format/edn) file that's specified on the command
line.  This configuration file contains the coordinates of the various endpoints, certificates, knickknacks and gewgaws
that Symphony needs in order for a bot to connect to a pod.  It also allows one to specify a blacklist of URL prefixes
that the bot should never, under any circumstances, unfurl.  Its structure as is follows:

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
  :url-blacklist ["<url prefix>" "<another url prefix>" "http://www.microsoft.com/"]
}

```

The `:symphony-coords` described above are passed directly to the
[clj-symphony library's `connect` function](https://github.com/symphonyoss/clj-symphony#usage),
and have the same semantics as what's described there.

## Usage

This bot will eventually be a standalone executable (e.g. a docker image), but
for now `lein run` it:

```
$ lein run -- -c <path to EDN configuration file>
```

## Developer Information

[GitHub project](https://github.com/pmonks/bot-unfurl)

[Bug Tracker](https://github.com/pmonks/bot-unfurl/issues)

## License

Copyright © 2016 Symphony Software Foundation

Distributed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).

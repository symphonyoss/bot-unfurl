FROM clojure:alpine
RUN mkdir -p /opt/bot-unfurl
RUN mkdir -p /etc/opt/bot-unfurl
WORKDIR /opt/bot-unfurl
COPY project.clj /opt/bot-unfurl/
RUN lein deps
COPY . /opt/bot-unfurl/
RUN mv "$(lein uberjar | sed -n 's/^Created \(.*standalone\.jar\)/\1/p')" bot-unfurl-standalone.jar
CMD ["java", "-jar", "/opt/bot-unfurl/bot-unfurl-standalone.jar", "-c", "/etc/opt/bot-unfurl/config.edn"]

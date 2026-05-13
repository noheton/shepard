FROM registry.access.redhat.com/ubi9/openjdk-21-runtime:1.24

COPY --chown=185 target/quarkus-app/lib/ /deployments/lib/
COPY --chown=185 target/quarkus-app/*.jar /deployments/
COPY --chown=185 target/quarkus-app/app/ /deployments/app/
COPY --chown=185 target/quarkus-app/quarkus/ /deployments/quarkus/
COPY --chown=185 target/classes/neo4j/ /deployments/neo4j/

# PM1a — plugin drop-in directory (ADR-0023). The backend's
# PluginRegistry walks this path at startup for `shepard-plugin-*.jar`
# files. The default `shepard.plugins.dir=backend/plugins` is overridden
# here to point at the in-container layout. Operators can mount their
# own directory over `/deployments/plugins` to add vendor plugins
# without rebaking the image.
USER 0
RUN mkdir -p /deployments/plugins && chown 185:0 /deployments/plugins
USER 185
ENV SHEPARD_PLUGINS_DIR=/deployments/plugins

EXPOSE 8080
ENV JAVA_OPTS_APPEND="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager -Dshepard.plugins.dir=/deployments/plugins"
ENV JAVA_APP_JAR="/deployments/quarkus-run.jar"

ENTRYPOINT [ "/opt/jboss/container/java/run/run-java.sh" ]


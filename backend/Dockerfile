FROM registry.access.redhat.com/ubi9/openjdk-21-runtime:1.24

COPY --chown=185 target/quarkus-app/lib/ /deployments/lib/
COPY --chown=185 target/quarkus-app/*.jar /deployments/
COPY --chown=185 target/quarkus-app/app/ /deployments/app/
COPY --chown=185 target/quarkus-app/quarkus/ /deployments/quarkus/
COPY --chown=185 target/classes/neo4j/ /deployments/neo4j/

# PM1a phase 3 — plugin drop-in directory (ADR-0023). The backend's
# PluginRegistry walks this path at startup for `shepard-plugin-*.jar`
# files. The default `shepard.plugins.dir=backend/plugins` is overridden
# here to point at the in-container layout. Operators can mount their
# own directory over `/deployments/plugins` to add vendor plugins
# without rebaking the image.
#
# UH1a's plugin JAR is *also* baked into `/deployments/lib/` because
# it ships as a backend Maven <dependency> (so Quarkus's build-time
# CDI scanner indexes its beans). The same JAR is additionally
# placed below in `/deployments/plugins/` so an operator listing
# the directory sees what's installed — the PluginRegistry's
# classpath-then-jar duplicate check silently shadows the JAR
# entry. Third-party plugin drop-ins (`cp foo.jar /deployments/plugins/`)
# also land here.
#
# The `plugins/` build-context staging is populated by the CI
# `build-images.yml` workflow (or a developer pre-step) before
# `docker build`; if the staging directory is empty/missing the
# COPY below is a no-op and the directory just stays empty —
# operators can drop in vendor plugins post-deploy.
USER 0
RUN mkdir -p /deployments/plugins && chown 185:0 /deployments/plugins
USER 185
COPY --chown=185 plugins-stage/ /deployments/plugins/
ENV SHEPARD_PLUGINS_DIR=/deployments/plugins

EXPOSE 8080
ENV JAVA_OPTS_APPEND="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager -Dshepard.plugins.dir=/deployments/plugins"
ENV JAVA_APP_JAR="/deployments/quarkus-run.jar"

ENTRYPOINT [ "/opt/jboss/container/java/run/run-java.sh" ]


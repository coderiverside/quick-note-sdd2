# Quick Note — JVM runtime image.
# Fast-jar layout gives sub-second startup without paying the native-build cost.
FROM registry.access.redhat.com/ubi9/openjdk-25-runtime:latest

ENV LANGUAGE='en_US:en' \
    JAVA_OPTS_APPEND="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"

USER 185
WORKDIR /deployments

COPY --chown=185 target/quarkus-app/lib/      /deployments/lib/
COPY --chown=185 target/quarkus-app/*.jar     /deployments/
COPY --chown=185 target/quarkus-app/app/      /deployments/app/
COPY --chown=185 target/quarkus-app/quarkus/  /deployments/quarkus/

EXPOSE 8080
ENTRYPOINT ["/opt/jboss/container/java/run/run-java.sh"]

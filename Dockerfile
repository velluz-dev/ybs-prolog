# Two stages so the runtime image carries a JRE and a jar, not Maven and a build cache.
#
# The frontend has no build step - pom.xml copies ../frontend into the jar as static
# resources - so there is no Node stage here and deliberately so. One process, one artifact.

# --- build -------------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src

# Dependencies first, in their own layer. They change far less often than the source, so a
# code edit reuses this layer instead of re-downloading Spring Boot every build.
COPY backend/pom.xml backend/pom.xml
RUN mvn -q -f backend/pom.xml dependency:go-offline -B

# pom.xml reaches OUT of backend/ for ../frontend, so both have to be present and in the
# same relative positions they occupy in the repository.
COPY backend backend
COPY frontend frontend

# Tests need a live MySQL; the build stage has none. They run in CI or against a real
# database, not here - see docs/DEPLOY.md.
RUN mvn -q -f backend/pom.xml package -DskipTests -B

# --- run ---------------------------------------------------------------------------------
FROM eclipse-temurin:17-jre AS run
WORKDIR /app

# Not root. If something ever gets through the app, it should not land as uid 0.
# curl is for the healthcheck below. The JRE image does not ship one, and a HEALTHCHECK
# whose command is missing reports unhealthy forever - which looks exactly like an app that
# will not start.
RUN apt-get update  && apt-get install -y --no-install-recommends curl  && rm -rf /var/lib/apt/lists/*  && useradd --system --uid 10001 --home /app ybs
COPY --from=build --chown=ybs:ybs /src/backend/target/*.jar /app/ybs.jar
USER ybs

EXPOSE 8081

# MaxRAMPercentage rather than a fixed -Xmx: the JVM then sizes its heap from whatever the
# container is actually given, so the same image behaves on a 512 MB box and a 4 GB one.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"
ENV SPRING_PROFILES_ACTIVE=prod

# The app is up when it answers, not when the process exists - Spring takes a few seconds to
# build the network cache and load the rule base, and a proxy that routes to it before then
# serves errors to real users.
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=5 \
    CMD ["sh", "-c", "curl -fsS http://localhost:8081/api/health || exit 1"]

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/ybs.jar"]

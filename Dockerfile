# syntax=docker/dockerfile:1.7

FROM maven:3.9.11-eclipse-temurin-17 AS build

WORKDIR /workspace
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -DskipTests dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -DskipTests package

FROM eclipse-temurin:17-jre-jammy

ARG VCS_REF=unknown
LABEL org.opencontainers.image.title="Vmq" \
      org.opencontainers.image.revision="${VCS_REF}"

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 vmq \
    && useradd --uid 10001 --gid vmq --home-dir /data --create-home vmq

WORKDIR /app
COPY --from=build --chown=10001:10001 \
    /workspace/target/mq-0.0.1-SNAPSHOT.war /app/vmq.war

USER vmq

ENV JAVA_OPTS="-Xms128m -Xmx448m -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD curl --fail --silent --show-error http://127.0.0.1:8080/ >/dev/null || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/vmq.war"]

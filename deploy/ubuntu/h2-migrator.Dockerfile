FROM maven:3.9.11-eclipse-temurin-17 AS tools

RUN mvn -B dependency:copy \
        -Dartifact=com.h2database:h2:1.4.197 \
        -DoutputDirectory=/tools \
    && mvn -B dependency:copy \
        -Dartifact=com.h2database:h2:2.3.232 \
        -DoutputDirectory=/tools

FROM eclipse-temurin:17-jre-jammy

ARG VCS_REF=unknown
LABEL org.opencontainers.image.title="Vmq H2 Migrator" \
      org.opencontainers.image.revision="${VCS_REF}"

COPY --from=tools /tools/h2-1.4.197.jar /opt/h2/h2-1.4.197.jar
COPY --from=tools /tools/h2-2.3.232.jar /opt/h2/h2-2.3.232.jar
COPY deploy/ubuntu/migrate-h2-1.4-to-2.3.sh /usr/local/bin/migrate-vmq-h2

ENTRYPOINT ["/usr/local/bin/migrate-vmq-h2"]

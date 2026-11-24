# Packages the jar that `./mvnw verify` already built and tested, so the shipped artifact is the tested one.
# Build:  ./mvnw -B verify && docker build -t hello .
# Base image is pinned by digest; Dependabot proposes updates.

FROM eclipse-temurin:21.0.1_12-jre-alpine@sha256:9a21ac97b76e52f4b58d5d6c7fdd459cd600cce8724a31fc0a2b4346b35bced2 AS extract
WORKDIR /build
COPY target/app.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --destination extracted

FROM eclipse-temurin:21.0.1_12-jre-alpine@sha256:9a21ac97b76e52f4b58d5d6c7fdd459cd600cce8724a31fc0a2b4346b35bced2
RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app -H -s /sbin/nologin app
WORKDIR /app
# Least-changing layers first so rebuilds reuse the cache.
COPY --from=extract /build/extracted/dependencies/ ./
COPY --from=extract /build/extracted/spring-boot-loader/ ./
COPY --from=extract /build/extracted/snapshot-dependencies/ ./
COPY --from=extract /build/extracted/application/ ./
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=30s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1:8080/healthz || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]

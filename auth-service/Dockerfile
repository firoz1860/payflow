# syntax=docker/dockerfile:1
# Build context is the repository ROOT:  docker build -f auth-service/Dockerfile .
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# The reactor parent lists every module, so Maven needs all the poms even when
# building a single service. Copying poms before src keeps the dep layer cached.
COPY pom.xml .
COPY payflow-common/pom.xml payflow-common/
COPY api-gateway/pom.xml api-gateway/
COPY auth-service/pom.xml auth-service/
COPY merchant-service/pom.xml merchant-service/
COPY payment-service/pom.xml payment-service/
COPY provider-service/pom.xml provider-service/
COPY ledger-service/pom.xml ledger-service/
RUN mvn -B -q -pl payflow-common,auth-service -am dependency:go-offline -DskipTests || true

COPY payflow-common/src payflow-common/src
COPY auth-service/src auth-service/src
RUN mvn -B -pl auth-service -am clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine AS runtime
RUN apk add --no-cache wget curl tzdata && addgroup -S payflow && adduser -S payflow -G payflow
WORKDIR /app
COPY --from=build /build/auth-service/target/*.jar app.jar
RUN chown -R payflow:payflow /app
USER payflow
ENV TZ=UTC
EXPOSE 8081
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=6 \
  CMD wget -qO- http://localhost:8081/actuator/health/liveness || exit 1
ENTRYPOINT ["sh","-c","exec java $JAVA_TOOL_OPTIONS -XX:+ExitOnOutOfMemoryError -Duser.timezone=UTC -jar /app/app.jar"]
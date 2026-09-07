# Build the application. Dependencies are resolved in their own layer so that editing source
# does not re-download the world on every rebuild.
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
# Windows checkouts hand over mvnw without the executable bit.
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src/ src/
# Tests need Docker (Testcontainers) and already ran in CI; running them here would need
# docker-in-docker for no added confidence.
RUN ./mvnw -B -q -DskipTests package

# Split the fat jar into layers that change at different rates.
FROM eclipse-temurin:21-jre-alpine AS extract
WORKDIR /builder
COPY --from=build /build/target/*.jar application.jar
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
USER app
WORKDIR /application
COPY --from=extract --chown=app:app /builder/extracted/dependencies/ ./
COPY --from=extract --chown=app:app /builder/extracted/spring-boot-loader/ ./
COPY --from=extract --chown=app:app /builder/extracted/snapshot-dependencies/ ./
COPY --from=extract --chown=app:app /builder/extracted/application/ ./
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=5 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "application.jar"]

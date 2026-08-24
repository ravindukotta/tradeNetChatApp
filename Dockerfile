# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copy the pom first so dependency resolution is cached in its own layer and only
# re-runs when pom.xml actually changes, not on every source edit.
COPY pom.xml .
RUN mvn -q -B dependency:go-offline

COPY src ./src
RUN mvn -q -B package -DskipTests

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Run as a non-root user - no reason for the JVM process to have root inside the
# container. /data is created (and owned by this user) before the volume mount so a
# fresh named volume inherits the right ownership on first run.
RUN addgroup -S app && adduser -S app -G app \
    && mkdir -p /data && chown -R app:app /data
COPY --from=build /build/target/tradenet-chat-application-*.jar app.jar

USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

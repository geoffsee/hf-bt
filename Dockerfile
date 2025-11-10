# syntax=docker/dockerfile:1.7

# ---- Builder stage ----
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /workspace/app

# Leverage Docker layer caching for dependencies
COPY gradle ./gradle
COPY gradlew .
COPY settings.gradle.kts build.gradle.kts ./
RUN chmod +x ./gradlew \
    && ./gradlew --version

# Copy sources last to maximize cache hits
COPY src ./src

# Build the executable Spring Boot fat jar (skip tests for speed inside container)
RUN ./gradlew clean bootJar -x test

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre AS runner
LABEL org.opencontainers.image.source="https://example.invalid/hf-bt" \
      org.opencontainers.image.description="HuggingSwarm Master API (demo/stub)"

# Create non-root user
RUN useradd -u 10001 -ms /bin/bash appuser
USER appuser
WORKDIR /app

# JVM tuning defaults for containers; can be overridden via JAVA_OPTS
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:InitialRAMPercentage=50.0 -XX:MaxRAMPercentage=80.0 -Djava.security.egd=file:/dev/./urandom"

# Copy built jar
COPY --from=builder /workspace/app/build/libs/*.jar /app/app.jar

EXPOSE 8080

# Default Spring profile remains "default"; additional properties can be passed via env
# Example: -e DEMO_PEER_ENABLED=true -e DEMO_PEER_URL=https://peer:4443/.well-known/webtransport
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]

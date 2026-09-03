# ==============================================================================
# Stage 1: Build Application Fat JAR
# ==============================================================================
FROM gradle:8.8-jdk21-alpine AS builder
WORKDIR /app

# Cache dependencies
COPY gradle/ gradle/
COPY gradlew build.gradle.kts settings.gradle.kts gradle.properties ./
RUN ./gradlew dependencies --no-daemon || true

# Copy source code and build standalone fat JAR
COPY src/ src/
RUN ./gradlew buildFatJar --no-daemon

# ==============================================================================
# Stage 2: Production Runtime
# ==============================================================================
FROM eclipse-temurin:21-jre-alpine AS runner
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=builder --chown=appuser:appgroup /app/build/libs/server-all.jar app.jar

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]

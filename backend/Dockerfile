# =============================================================================
# Stage 1: Build & Package Spring Boot Application
# =============================================================================
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Cache dependencies layer
COPY pom.xml .
RUN mvn -B dependency:go-offline -DexcludeArtifactIds=smartpark-backend || true

# Copy source code and build production jar
COPY src ./src
RUN mvn -B clean package -DskipTests

# =============================================================================
# Stage 2: Minimal Production Runtime
# =============================================================================
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Run as dedicated unprivileged user for container security
RUN addgroup -S smartpark && adduser -S smartpark -G smartpark

# Copy packaged jar from builder stage
COPY --from=builder --chown=smartpark:smartpark /build/target/*.jar app.jar

USER smartpark:smartpark

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=35s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]

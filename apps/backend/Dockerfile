# Multi-stage Dockerfile for SA Command Backend on Render Free
# Stage 1: Build artifact
FROM maven:3.9.9-eclipse-temurin-21-jammy AS build
WORKDIR /workspace

# Cache dependency layer
COPY pom.xml .
RUN mvn -B dependency:go-offline -DskipTests || true

# Copy source code and package
COPY src ./src
RUN mvn -B clean package -DskipTests

# Stage 2: Minimal Production Runtime
FROM eclipse-temurin:21-jre-jammy

# Create non-root user
RUN groupadd -r appgroup && useradd -r -g appgroup -d /app -s /sbin/nologin appuser

WORKDIR /app

# Copy the built jar and entrypoint
COPY --from=build /workspace/target/*.jar /app/app.jar
COPY entrypoint.sh /app/entrypoint.sh

RUN chmod +x /app/entrypoint.sh && chown -R appuser:appgroup /app

USER appuser

# Render Free 512MB RAM constraints:
# Constrain heap to 220m, metaspace to 112m, code cache to 48m, thread stack to 256k
# Use Serial GC for minimal native memory overhead
ENV JAVA_OPTS="-XX:+UseSerialGC -Xms128m -Xmx220m -XX:MaxMetaspaceSize=112m -XX:ReservedCodeCacheSize=48m -Xss256k -XX:+ExitOnOutOfMemoryError"
ENV PORT=8080

EXPOSE 8080

ENTRYPOINT ["/app/entrypoint.sh"]

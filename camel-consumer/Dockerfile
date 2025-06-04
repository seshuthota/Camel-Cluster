FROM eclipse-temurin:17-jre

WORKDIR /app

# Install curl for health checks
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Copy the pre-built JAR
COPY target/camel-consumer-*.jar app.jar

# Create directories for file operations
RUN mkdir -p /app/data/input /app/data/output /app/data/processed

# Expose port
EXPOSE 8082

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8082/api/consumer/health || exit 1

# Run application with Docker profile
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=docker"] 
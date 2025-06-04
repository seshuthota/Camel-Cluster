FROM eclipse-temurin:17-jre

WORKDIR /app

# Install curl for health checks
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Copy the pre-built JAR
COPY target/camel-coordinator-*.jar app.jar

# Create directories for file operations
RUN mkdir -p /app/data/reports /app/data/logs

# Expose port
EXPOSE 8083

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8083/api/coordinator/health || exit 1

# Run application with Docker profile
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=docker"] 
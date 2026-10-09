# Use Amazon Corretto 21 as the base image
FROM docker.io/library/amazoncorretto:25-alpine

# Install ffmpeg/ffprobe for video validation and thumbnail extraction
RUN apk add --no-cache ffmpeg

# Set the working directory inside the container
WORKDIR /app

# Copy the JAR file into the container
COPY target/*.jar feedzmore-service.jar

# Create a directory for configuration files
RUN mkdir -p /app/config

# Copy all configuration files (including application.yml and profiles)
COPY src/main/resources/application*.yml /app/config/

# Expose port 9091 for Spring Boot
EXPOSE 9091

# Run the application with explicit config location
ENTRYPOINT ["java", "-jar", "-Dspring.config.location=file:/app/config/", "/app/feedzmore-service.jar"]
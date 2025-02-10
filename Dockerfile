# Use amazon correto 21 as the base image
FROM amazoncorretto:21

# Set the working directory inside the container
WORKDIR /app

# Copy the JAR file into the container
COPY target/*.jar feedzmore-service.jar

# Expose port 9091 for springboot to run
EXPOSE 9091

# Run the application
ENTRYPOINT ["java", "-jar", "/app/feedzmore-service.jar"]

# Plain Dockerfile syntax only: the Jenkins agent builds with the legacy builder,
# which does not support BuildKit features such as RUN --mount.
# The jar is built beforehand by the Jenkins "Package" stage (target/*.jar).

# Stage 1: split the fat jar into layers so unchanged dependencies are reused
# between builds, pushes and pulls; only the small "application" layer changes per commit.
FROM docker.io/library/eclipse-temurin:25-jre-alpine AS layers
WORKDIR /layers
COPY target/*.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

# Stage 2: runtime. A JRE is enough to run the app; video thumbnails are produced
# by feedzmore-media-worker, so ffmpeg is not needed here anymore.
FROM docker.io/library/eclipse-temurin:25-jre-alpine
WORKDIR /app

# Least-changing layers first.
COPY --from=layers /layers/extracted/dependencies/ ./
COPY --from=layers /layers/extracted/spring-boot-loader/ ./
COPY --from=layers /layers/extracted/snapshot-dependencies/ ./
COPY --from=layers /layers/extracted/application/ ./

# Configuration files (including application.yml and profiles)
COPY src/main/resources/application*.yml /app/config/

# Expose port 9091 for Spring Boot
EXPOSE 9091

# Run the application with explicit config location
ENTRYPOINT ["java", "-Dspring.config.location=file:/app/config/", "org.springframework.boot.loader.launch.JarLauncher"]

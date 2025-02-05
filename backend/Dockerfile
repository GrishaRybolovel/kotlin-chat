# Stage 1: Build the application
FROM gradle:8.0.2-jdk17 AS builder
WORKDIR /app
# Copy the Gradle wrapper and configuration files (without gradlew.bat)
COPY gradlew gradle/ /app/gradle/
COPY build.gradle.kts settings.gradle.kts /app/
# Copy source code
COPY src /app/src
# Build the fat JAR (adjust the task name if necessary)
RUN gradle clean shadowJar --no-daemon

# Stage 2: Run the application
FROM openjdk:17-slim
WORKDIR /app
# Use a wildcard to copy the fat JAR from the builder stage
COPY --from=builder /app/build/libs/*-all*.jar app.jar
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]

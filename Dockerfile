# Use an official Maven image to build the project
FROM maven:3.8.6-openjdk-18 AS build

# Set the working directory
WORKDIR /app

# Copy the pom.xml and download dependencies
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy the rest of the project files
COPY src ./src


# Package the application
RUN mvn package

# Use a smaller base image for the runtime
FROM openjdk:18-jdk-alpine

# Set the working directory
WORKDIR /app

# Copy the packaged jar file from the build stage
COPY --from=build /app/target/new_pipeline_kafka_dns_streamer-1.0-SNAPSHOT-jar-with-dependencies.jar app.jar


# Specify the command to run the application
ENTRYPOINT ["java", "-jar", "app.jar"]

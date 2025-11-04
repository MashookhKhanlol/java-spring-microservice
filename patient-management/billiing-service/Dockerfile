# ---- Build Stage ----
FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /app

# Copy Maven descriptor first to leverage caching
COPY pom.xml .

# Download dependencies
RUN mvn dependency:go-offline -B

# Copy source and build the project
COPY src ./src
RUN mvn -B clean package -DskipTests


# ---- Runtime Stage ----
# Use JRE for smaller image size (lighter than JDK)
FROM eclipse-temurin:21-jre AS runner

WORKDIR /app

# Copy the built JAR from the builder stage
COPY --from=builder /app/target/*.jar app.jar

# Expose service and management ports
EXPOSE 4001
EXPOSE 9001

# Run the Spring Boot application
ENTRYPOINT ["java", "-jar", "app.jar"]

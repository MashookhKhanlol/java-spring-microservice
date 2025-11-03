# ================================
# 🧱 Build Stage
# ================================
FROM --platform=linux/amd64 maven:3.9.9-eclipse-temurin-21 AS builder

# Set working directory
WORKDIR /app

# Copy the Maven project descriptor first (for dependency caching)
COPY pom.xml .

# Download dependencies (go offline to speed up subsequent builds)
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Package the application (skip tests for faster build)
RUN mvn clean package -DskipTests

# ================================
# 🚀 Runtime Stage
# ================================
FROM --platform=linux/amd64 openjdk:21-jdk-slim AS runner

WORKDIR /app

# Copy built JAR from the builder stage
COPY --from=builder /app/target/patient-service-0.0.1-SNAPSHOT.jar app.jar

# Expose application port
EXPOSE 4000

# Set default command
ENTRYPOINT ["java", "-jar", "app.jar"]

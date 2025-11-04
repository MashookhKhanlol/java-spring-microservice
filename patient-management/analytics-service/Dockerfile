# ===== Build stage =====
FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /app

# Copy and prepare dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build
COPY src ./src
RUN mvn clean package -DskipTests

# ===== Runtime stage =====
FROM eclipse-temurin:21-jdk AS runner

WORKDIR /app

# Copy the built JAR from builder
COPY --from=builder /app/target/analytics-service-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 4002

ENTRYPOINT ["java", "-jar", "app.jar"]

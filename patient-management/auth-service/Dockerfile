# ===== Stage 1: Build =====
FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /app

# Copy pom and source
COPY pom.xml .
RUN mvn dependency:go-offline

COPY src ./src
RUN mvn clean package -DskipTests

# ===== Stage 2: Runtime =====
FROM eclipse-temurin:21-jdk AS runner
WORKDIR /app

# Copy jar from builder stage
COPY --from=builder /app/target/*.jar app.jar

# Run the app
ENTRYPOINT ["java", "-jar", "app.jar"]

# ================================
# 🧱 Build Stage
# ================================
FROM --platform=linux/amd64 maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /app

# Copy Maven descriptor first (for caching)
COPY pom.xml .

# Pre-download dependencies
RUN mvn dependency:go-offline -B

# Copy source and build
COPY src ./src
RUN mvn clean package -DskipTests

# ================================
# 🚀 Runtime Stage
# ================================
FROM --platform=linux/amd64 eclipse-temurin:21-jre AS runner

WORKDIR /app

# Copy built jar from builder
COPY --from=builder /app/target/*.jar app.jar

EXPOSE 4004

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]

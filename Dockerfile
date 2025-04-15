# ---------- Stage 1: Build ----------
FROM maven:3.9.4-eclipse-temurin-17 AS builder
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# ---------- Stage 2: Run ----------
FROM openjdk:17-jdk-slim
WORKDIR /app

# Copy jar đã build từ stage 1
COPY --from=builder /app/target/*.jar app.jar

# Copy .env để dotenv-java đọc
COPY .env .env

# Set entrypoint đảm bảo .env được Spring load qua dotenv-java
ENTRYPOINT ["sh", "-c", "java -jar app.jar"]

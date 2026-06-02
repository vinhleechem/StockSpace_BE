# Stage 1: Build stage
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /app

# Copy pom.xml và source code
COPY pom.xml .
COPY src ./src

# Build package jar file (bỏ qua chạy tests để tránh lỗi thiếu DB lúc build)
RUN mvn clean package -DskipTests

# Stage 2: Run stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy jar file từ build stage
COPY --from=build /app/target/StockSpace_BE-0.0.1-SNAPSHOT.jar app.jar

# Expose port 8080
EXPOSE 8080

# Chạy ứng dụng
ENTRYPOINT ["java", "-jar", "app.jar"]

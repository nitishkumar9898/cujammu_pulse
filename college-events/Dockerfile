# Build stage
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline

COPY src ./src
RUN mvn clean package -DskipTests

# Run stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create upload directory
RUN mkdir -p /app/uploads && chmod 777 /app/uploads

# Copy jar from build stage
COPY --from=build /app/target/*.jar app.jar

# Environment defaults
ENV SERVER_PORT=8080
ENV SPRING_PROFILES_ACTIVE=prod
ENV APP_UPLOAD_DIR=/app/uploads

EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD wget -q --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
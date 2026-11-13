# Build from the repository root: docker build -f college-events/Dockerfile .
# Official image index digests verified against registry metadata on 2026-10-10.
FROM node:24-alpine@sha256:ebfe2f90462722a7a4de65e91990e97fe0d401c70e0e762c5b53302f905ec1c1 AS web
WORKDIR /web
COPY cujammu-pulse/package.json cujammu-pulse/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY cujammu-pulse/ ./
RUN npm run typecheck && npm test && npm run build:web

FROM maven:3.9.16-eclipse-temurin-21-alpine@sha256:308cba8b638ed7e4658cea3f8399066219466211c805f6d5728c3c9c7614661b AS build
WORKDIR /app
COPY college-events/pom.xml .
RUN mvn dependency:go-offline

COPY college-events/src ./src
RUN mvn --batch-mode clean verify

# Run stage
FROM eclipse-temurin:21-jre-alpine@sha256:51ab5e3302e7141ce665ca3ea85e8b5cd648eafbc3c0c90dd79d6537684e4555
WORKDIR /app

# Create upload directory
RUN addgroup -S -g 10001 pulse && adduser -S -D -H -u 10001 -G pulse pulse \
    && mkdir -p /app/uploads /app/data /app/logs /app/web \
    && chown -R pulse:pulse /app && chmod 750 /app/uploads /app/data /app/logs

# Copy jar from build stage
COPY --from=build /app/target/*.jar app.jar
COPY --from=web /web/dist/ /app/web/

# Environment defaults
ENV SERVER_PORT=8080
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_TOOL_OPTIONS="-Duser.timezone=Asia/Kolkata"
ENV APP_UPLOAD_DIR=/app/uploads
ENV APP_CLIENT_DIST=/app/web

USER 10001:10001

EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD wget -q --spider http://localhost:8080/api/ready || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]

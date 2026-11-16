FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace
COPY pom.xml ./
COPY src ./src
RUN mvn -q -DskipTests package
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S agrocenter && adduser -S agrocenter -G agrocenter
WORKDIR /app
COPY --from=build --chown=agrocenter:agrocenter /workspace/target/ms-kinesis-admin-*.jar app.jar
USER agrocenter
EXPOSE 8087
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

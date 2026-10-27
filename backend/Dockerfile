FROM maven:3.9.10-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine-3.23
WORKDIR /app
RUN apk add --no-cache curl \
    && apk del --no-cache gnupg \
    && addgroup -S spendify \
    && adduser -S -D -H -u 10001 -G spendify spendify
COPY --from=build /workspace/target/spendify-backend-*.jar /app/spendify.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/spendify.jar"]

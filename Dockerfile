FROM maven:3.9.9-eclipse-temurin-21 AS build
ARG SERVICE
WORKDIR /workspace
COPY . .
RUN mvn -s .mvn/settings.xml -B -pl ${SERVICE} -am -DskipTests package

FROM eclipse-temurin:21-jre-alpine
ARG SERVICE
RUN addgroup -S booking && adduser -S booking -G booking
WORKDIR /app
COPY --from=build /workspace/${SERVICE}/target/*-exec.jar /app/app.jar
USER booking
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]

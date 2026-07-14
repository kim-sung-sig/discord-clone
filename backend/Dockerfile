FROM gradle:8.11.1-jdk21 AS build
ARG SERVICE
ARG SERVICE_PATH
WORKDIR /workspace
COPY . .
RUN gradle :${SERVICE}:bootJar --no-daemon
RUN cp backend/services/${SERVICE_PATH}/build/libs/*.jar /app/app.jar

FROM eclipse-temurin:21-jre
COPY --from=build /app/app.jar /app/app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

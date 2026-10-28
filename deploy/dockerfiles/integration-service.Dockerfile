FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace
COPY pom.xml .
COPY common/pom.xml common/pom.xml
COPY services/auth-service/pom.xml services/auth-service/pom.xml
COPY services/parcel-service/pom.xml services/parcel-service/pom.xml
COPY services/operations-service/pom.xml services/operations-service/pom.xml
COPY services/integration-service/pom.xml services/integration-service/pom.xml
COPY common common
COPY services/integration-service services/integration-service
RUN mvn -B -pl services/integration-service -am package -Dmaven.test.skip=true

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl
WORKDIR /app
COPY --from=build /workspace/services/integration-service/target/integration-service-*.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

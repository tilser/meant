FROM maven:3.9.16-eclipse-temurin-25 AS build

WORKDIR /workspace

COPY pom.xml ./
RUN mvn -B -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -Dmaven.test.skip=true package

FROM eclipse-temurin:25

WORKDIR /app

COPY --from=build --chown=10001:10001 /workspace/target/meant-api-*.jar /app/meant-api.jar

USER 10001:10001

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/meant-api.jar"]

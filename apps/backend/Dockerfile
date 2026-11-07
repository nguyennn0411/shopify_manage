FROM maven:3.9-eclipse-temurin-21 AS dependencies
WORKDIR /workspace
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline

FROM dependencies AS test
COPY src src
RUN --mount=type=cache,target=/root/.m2 mvn -B test

FROM dependencies AS build
COPY src src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests

FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* && useradd --system --uid 10001 --create-home spring
WORKDIR /app
COPY --from=build /workspace/target/erp-backend-1.0.0.jar app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-Djava.security.egd=file:/dev/urandom","-jar","/app/app.jar"]

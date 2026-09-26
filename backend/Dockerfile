FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY backend/pom.xml pom.xml
COPY backend/maven-settings-aliyun.xml maven-settings.xml
COPY backend/src src
RUN mvn -s /build/maven-settings.xml -B -DskipTests package

FROM eclipse-temurin:17-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* \
    && groupadd --system workbench && useradd --system --gid workbench workbench
WORKDIR /app
COPY --from=build --chown=workbench:workbench /build/target/backend-0.0.1-SNAPSHOT.jar app.jar
USER workbench
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app

COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
COPY src/main ./src/main

RUN sed -i 's/\r$//' gradlew \
    && sh ./gradlew --no-daemon bootJar \
    && cp build/libs/*.jar /app/app.jar

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

COPY --from=build --chown=10001:10001 /app/app.jar ./app.jar

USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

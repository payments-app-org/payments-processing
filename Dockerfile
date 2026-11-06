# syntax=docker/dockerfile:1
# Сборка из корня репозитория. Общая библиотека payments-platform скачивается из GitHub Packages: токен передаётся
# секретом сборки (github_token) — в слои образа он не попадает. Тесты здесь не запускаем: их уже прогнал CI.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
ENV GITHUB_ACTOR=payments-build
COPY .mvn .mvn
COPY pom.xml pom.xml
RUN --mount=type=cache,target=/root/.m2 --mount=type=secret,id=github_token,env=GITHUB_TOKEN \
    mvn -q -B -s .mvn/settings.xml dependency:go-offline
COPY src src
RUN --mount=type=cache,target=/root/.m2 --mount=type=secret,id=github_token,env=GITHUB_TOKEN \
    mvn -q -B -s .mvn/settings.xml package -DskipTests && cp target/processing-*.jar /app.jar

FROM eclipse-temurin:21-jre
# Не root: у процесса в контейнере нет лишних прав
RUN useradd --system --uid 10001 app
USER app
COPY --from=build /app.jar /app.jar
# Куча — доля лимита памяти контейнера; при нехватке памяти процесс падает, и его перезапускают
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "/app.jar"]

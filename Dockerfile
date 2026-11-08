FROM maven:3.9-eclipse-temurin-21 AS build
ARG MODULE
WORKDIR /build

COPY ${MODULE}/pom.xml ${MODULE}/pom.xml
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -f ${MODULE}/pom.xml dependency:go-offline

COPY ${MODULE}/src ${MODULE}/src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -f ${MODULE}/pom.xml -DskipTests package \
    && cp ${MODULE}/target/*.jar /build/app.jar


FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --create-home app
WORKDIR /app
COPY --from=build /build/app.jar app.jar
USER app
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

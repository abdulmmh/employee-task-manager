FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline
COPY src/ src/
# Tests are run by the developer before deployment; packaging needs no live DB.
RUN ./mvnw -B -ntp -DskipTests package

FROM eclipse-temurin:17-jre-jammy
RUN groupadd --gid 10001 etm && useradd --uid 10001 --gid etm --no-create-home --shell /usr/sbin/nologin etm
WORKDIR /app
COPY --from=build --chown=etm:etm /workspace/target/*.jar /app/app.jar
COPY --chown=etm:etm docker-entrypoint.sh /app/docker-entrypoint.sh
RUN chmod 500 /app/docker-entrypoint.sh
USER 10001:10001
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=50.0 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["/app/docker-entrypoint.sh"]

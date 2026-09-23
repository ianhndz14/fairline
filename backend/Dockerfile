# Build the jar with Maven, then run it on a slim JRE image.
FROM maven:3-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn --batch-mode --no-transfer-progress dependency:go-offline
COPY src ./src
RUN mvn --batch-mode --no-transfer-progress package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --create-home --shell /usr/sbin/nologin fairline
COPY --from=build /app/target/fairline-*.jar app.jar
USER fairline
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

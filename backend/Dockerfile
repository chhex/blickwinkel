# Stufe 1: bauen. Auf dem Zielrechner braucht es so weder Java noch Maven.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Abhängigkeiten zuerst: Diese Schicht bleibt gecacht, solange pom.xml gleich bleibt
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q package

# Stufe 2: nur Laufzeit
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --no-create-home app
COPY --from=build /build/target/blickwinkel-*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

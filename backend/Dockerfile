# Etapa 1: compila el jar con Maven (sin correr tests: los corre el pipeline).
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

# Etapa 2: imagen de ejecución mínima, con un usuario sin privilegios.
FROM eclipse-temurin:17-jre-jammy
RUN groupadd --system logired && useradd --system --gid logired --uid 1001 logired
WORKDIR /app
COPY --from=build /app/target/backend-0.0.1-SNAPSHOT.jar app.jar
USER logired
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

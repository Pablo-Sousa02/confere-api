FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
COPY src/main/ src/main/
# Testes são executados antes da imagem; o build não exige Docker dentro do Docker.
RUN ./mvnw --batch-mode --no-transfer-progress '-Dmaven.test.skip=true' package

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
RUN addgroup -S -g 10001 confere && adduser -S -u 10001 -G confere confere
COPY --from=build --chown=10001:10001 /workspace/target/confere-0.0.1-SNAPSHOT.jar /app/app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=50.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]

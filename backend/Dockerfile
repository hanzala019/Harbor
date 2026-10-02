FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src src
RUN mvn -B -ntp package
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN groupadd --system hospital && useradd --system --gid hospital hospital
COPY --from=build /app/target/hospital-1.0.0.jar app.jar
USER hospital
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=65", "-jar", "app.jar"]

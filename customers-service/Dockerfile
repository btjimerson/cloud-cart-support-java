FROM maven:3.9.10-eclipse-temurin-21 AS build
COPY pom.xml /home/pom.xml
COPY customers-service/pom.xml /home/customers-service/pom.xml
COPY customers-service/src /home/customers-service/src
RUN mvn -f /home/customers-service/pom.xml clean package -DskipTests

FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
RUN useradd -r -u 1001 appuser
WORKDIR /opt
EXPOSE 8080
COPY --from=build /home/customers-service/target/*.jar /opt/app.jar
USER appuser
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1
CMD ["java", "-jar", "/opt/app.jar"]

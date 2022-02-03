#
# Build stage
#
FROM maven:3.6.0-jdk-11-slim AS build
COPY src /home/app/src
COPY pom.xml /home/app
RUN mvn -f /home/app/pom.xml clean package

#
# Package stage
#
FROM openjdk:11-jre-slim
COPY --from=build /home/app/target/kohesio-backend-*-SNAPSHOT.jar /usr/local/lib/kohesio-backend.jar
COPY --from=build /home/app/src/main/resources/config/application-k8s-prod.properties /home/app/application-k8s-prod.properties

EXPOSE 5678
ENTRYPOINT ["java","-Dspring.config.location=file:///home/app/application-k8s-prod.properties","-jar", "/usr/local/lib/kohesio-backend.jar"]
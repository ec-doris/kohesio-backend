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
EXPOSE 5678
ENTRYPOINT ["java","-jar","/usr/local/lib/kohesio-backend.jar"]
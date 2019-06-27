FROM maven:3.6.1-jdk-12 AS build

WORKDIR /build

COPY pom.xml .

RUN mvn package

COPY src ./src

RUN mvn package

FROM openjdk:12-alpine

WORKDIR /app
COPY --from=build /build/target/nginx-www-operator.jar .

CMD java -jar nginx-www-operator.jar

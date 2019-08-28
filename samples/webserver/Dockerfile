FROM openjdk:12-alpine

ENTRYPOINT ["java", "-jar", "/usr/share/operator/operator.jar"]

ARG JAR_FILE
ADD target/${JAR_FILE} /usr/share/operator/operator.jar


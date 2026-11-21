FROM maven:3.9-eclipse-temurin-21-alpine

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn package

EXPOSE 8080 8081

CMD ["java", "-jar", "target/sentiment-spring.jar"]

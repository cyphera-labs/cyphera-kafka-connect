FROM maven:3.9-eclipse-temurin-11
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src/ src/
COPY config/ config/
RUN mvn package -B -q -DskipTests

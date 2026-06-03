FROM maven:3.9.9-eclipse-temurin-21-alpine AS build

WORKDIR /app

COPY pom.xml ./
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

FROM maven:3.9.9-eclipse-temurin-21-alpine

WORKDIR /app

COPY --from=build /root/.m2 /root/.m2
COPY --from=build /app/pom.xml ./pom.xml
COPY --from=build /app/src ./src
COPY --from=build /app/target ./target

RUN mkdir -p /app/database

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/target/*.jar"]

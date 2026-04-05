FROM maven:3.9-eclipse-temurin-17-alpine AS builder

WORKDIR /build

COPY . .

RUN mvn package -pl services/user-service/user-service-src -am -DskipTests --no-transfer-progress -q

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY --from=builder /build/services/user-service/user-service-src/target/*.jar app.jar

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]
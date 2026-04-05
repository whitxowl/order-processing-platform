FROM maven:3.9-eclipse-temurin-17-alpine AS builder

WORKDIR /build

COPY . .

RUN mvn package -pl services/inventory-service -am -DskipTests --no-transfer-progress -q

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY --from=builder /build/services/inventory-service/target/*.jar app.jar

EXPOSE 8084
EXPOSE 9094

ENTRYPOINT ["java", "-jar", "app.jar"]
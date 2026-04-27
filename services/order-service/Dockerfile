FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

COPY . .

RUN mvn package -pl services/order-service/order-service-src -am -DskipTests --no-transfer-progress -q

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

COPY --from=builder /build/services/order-service/order-service-src/target/*.jar app.jar

EXPOSE 8085

ENTRYPOINT ["java", "-jar", "app.jar"]
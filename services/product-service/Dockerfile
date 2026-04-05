FROM maven:3.9-eclipse-temurin-17-alpine AS builder

WORKDIR /build

COPY . .

RUN mvn package -pl services/product-service/product-service-src -am -DskipTests --no-transfer-progress -q

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY --from=builder /build/services/product-service/product-service-src/target/*.jar app.jar

EXPOSE 8083

ENTRYPOINT ["java", "-jar", "app.jar"]
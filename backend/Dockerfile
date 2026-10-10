FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring

COPY --from=build /app/target/option-pricing-backend-0.0.1-SNAPSHOT.jar app.jar

USER spring:spring
EXPOSE 8080

# Sized for a 512 MB container: cap the heap at half of it and use the lightest collector,
# leaving the rest for class metadata, compiled code and thread stacks.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=50", "-XX:+UseSerialGC", "-Xss512k", "-jar", "/app/app.jar"]

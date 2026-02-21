# Этап 1: сборка JAR (образ с Maven + JDK 21)
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Копируем pom и исходники, собираем JAR внутри контейнера
COPY pom.xml .
COPY src ./src
RUN mvn package -DskipTests -B

# Этап 2: финальный образ
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Копируем JAR из этапа сборки
COPY --from=builder /build/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]

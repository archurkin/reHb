# Используем официальный образ OpenJDK 21
FROM eclipse-temurin:21-jre-alpine

# Создаем рабочую директорию
WORKDIR /app

# Копируем JAR файл
COPY target/*.jar app.jar

# Открываем порт (если нужен для health check)
EXPOSE 8080

# Запускаем приложение
ENTRYPOINT ["java", "-jar", "app.jar"]

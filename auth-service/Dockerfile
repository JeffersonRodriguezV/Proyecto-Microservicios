# ---------- Etapa 1: compilación ----------
# Imagen con Maven + JDK 21; el JAR se genera aquí y no viaja a la imagen final.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Se copia primero solo el pom para aprovechar la caché de capas de Docker:
# las dependencias se descargan de nuevo únicamente si el pom cambia.
COPY pom.xml .
RUN mvn -q -B dependency:go-offline

COPY src ./src
RUN mvn -q -B clean package -DskipTests

# ---------- Etapa 2: ejecución ----------
# JRE alpine: imagen pequeña y con wget (lo usa el healthcheck del compose).
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Buena práctica de seguridad: no ejecutar como root.
RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8086
ENTRYPOINT ["java", "-jar", "app.jar"]
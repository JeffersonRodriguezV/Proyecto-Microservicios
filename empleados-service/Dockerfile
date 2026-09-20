# ==========================================
# Etapa 1: build - compila el proyecto con Maven dentro del contenedor
# ==========================================
FROM maven:3.9-eclipse-temurin-21-alpine AS build

WORKDIR /build

# Copiamos primero el pom.xml para aprovechar la cache de capas de Docker
COPY pom.xml .
RUN mvn -B dependency:go-offline

# Copiamos el resto del código fuente y empaquetamos
COPY src ./src
RUN mvn -B clean package -DskipTests

# ==========================================
# Etapa 2: runtime - imagen ligera solo con el jar final
# ==========================================
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
# wget se instala explícitamente porque la imagen base Alpine no lo
# trae, y lo necesita el healthcheck definido en docker-compose.yml
# (mismo requisito que el propio Reto 2 menciona: "requiere que curl
# -o wget- esté en la imagen" para el healthcheck de un microservicio).
RUN apk add --no-cache wget

# Copiamos el jar generado en la etapa de build
COPY --from=build /build/target/gestionempleados-0.0.1-SNAPSHOT.jar app.jar

# Puerto que expone la aplicación (debe coincidir con server.port)
EXPOSE 8080

# Comando para ejecutar la aplicación al iniciar el contenedor
ENTRYPOINT ["java", "-jar", "app.jar"]
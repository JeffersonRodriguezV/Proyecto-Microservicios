# Etapa única: usamos una imagen ligera con Java 21
FROM eclipse-temurin:21-jre-alpine

# Directorio de trabajo dentro del contenedor
WORKDIR /app

# Copiamos el jar generado por Maven al contenedor
COPY target/gestionempleados-0.0.1-SNAPSHOT.jar app.jar

# Puerto que expone la aplicación (debe coincidir con server.port)
EXPOSE 8080

# Comando para ejecutar la aplicación al iniciar el contenedor
ENTRYPOINT ["java", "-jar", "app.jar"]
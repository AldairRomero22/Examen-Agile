# Etapa 1: compilar con Maven
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests package

# Etapa 2: ejecutar solo con el JRE
FROM eclipse-temurin:21-jre
WORKDIR /app
# HU-07: Tesseract (OCR) con español para leer los vouchers.
# fontconfig y fuentes: Java las necesita para dibujar las páginas de un PDF escaneado.
RUN apt-get update \
 && apt-get install -y --no-install-recommends tesseract-ocr tesseract-ocr-spa fontconfig fonts-dejavu-core \
 && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/target/castillo.jar app.jar
ENV JAVA_OPTS="-Xmx300m"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

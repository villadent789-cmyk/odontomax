# =============================================================================
# Dockerfile Multi-stage para OdontoMax (Spring Boot 3.3 / Java 17)
# Seguridad: Usuario no root, verificación de integridad y Healthcheck Actuator
# =============================================================================

# Etapa 1: Compilación con Maven
FROM maven:3.9.8-eclipse-temurin-17-alpine AS builder
WORKDIR /workspace

# Cachear dependencias de Maven
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Compilar artefacto excluyendo tests unitarios de empaquetado
COPY src ./src
RUN mvn clean package -DskipTests -B

# Etapa 2: Runtime Liviano y Seguro (JRE 17 Alpine)
FROM eclipse-temurin:17-jre-alpine

# Instalar curl para healthcheck
RUN apk add --no-cache curl

WORKDIR /app

# Crear usuario y grupo de ejecución sin privilegios de root (OWASP Docker Security)
RUN addgroup -g 10001 -S appgroup && \
    adduser -u 10001 -S appuser -G appgroup

# Copiar el JAR generado desde la etapa de compilación
COPY --from=builder /workspace/target/*.jar app.jar
RUN chown -R appuser:appgroup /app

# Cambiar a usuario no root
USER appuser:appgroup

# Puerto por defecto de Render / Cloud Hosts
ENV PORT=8080
EXPOSE 8080

# Healthcheck oficial apuntando al Actuator de Spring Boot
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
  CMD curl -f http://localhost:${PORT}/api/v1/actuator/health || exit 1

# TieredStopAtLevel=1 + SerialGC: arranque ~2x más rápido con poca CPU (medido con --cpus=0.25:
# ~130 s -> ~65 s), evitando el timeout de despliegue de Render. Para una clínica el rendimiento
# en caliente sigue sobrado.
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-XX:TieredStopAtLevel=1", "-XX:+UseSerialGC", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]

package com.odontomax.arquitectura;

import com.odontomax.OdontomaxApplication;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

public class ModulosTest {

    private static final Logger log = LoggerFactory.getLogger(ModulosTest.class);

    @Test
    void informeModulosYDocumentacion() throws IOException {
        ApplicationModules modules = ApplicationModules.of(OdontomaxApplication.class);

        log.info("=== SPRING MODULITH: MÓDULOS DETECTADOS ===");
        modules.forEach(module -> {
            log.info("Módulo detectado: {} (paquete: {})", module.getName(), module.getBasePackage());
        });

        log.info("=== SPRING MODULITH: MODO INFORME DE VIOLACIONES ===");
        var violations = modules.detectViolations();
        if (violations.hasViolations()) {
            log.warn("Se detectaron violaciones entre módulos (esperado en arquitectura por capas actual):");
            violations.getMessages().forEach(msg -> log.warn(" - {}", msg));
        } else {
            log.info("No se detectaron violaciones entre módulos.");
        }

        // Generar documentación con Documenter en target/spring-modulith-docs/
        new Documenter(modules).writeDocumentation();

        // Copiar diagramas a docs/migracion/modulith/ para registrar el "antes"
        copiarDocumentacionModulith();

        // TODO: En Fase 7 (endurecer), cambiar a modules.verify() para que cualquier ciclo o violación rompa el build
    }

    private void copiarDocumentacionModulith() throws IOException {
        Path origen = Paths.get("target", "spring-modulith-docs");
        Path destino = Paths.get("..", "docs", "migracion", "modulith");

        if (!Files.exists(origen)) {
            origen = Paths.get("backend", "target", "spring-modulith-docs");
            destino = Paths.get("docs", "migracion", "modulith");
        }

        if (Files.exists(origen)) {
            Files.createDirectories(destino);
            Path finalOrigen = origen;
            Path finalDestino = destino;
            try (Stream<Path> stream = Files.walk(origen)) {
                stream.forEach(source -> {
                    Path target = finalDestino.resolve(finalOrigen.relativize(source).toString());
                    try {
                        if (Files.isDirectory(source)) {
                            Files.createDirectories(target);
                        } else {
                            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                        }
                    } catch (IOException e) {
                        log.error("Error copiando archivo de documentación {}: {}", source, e.getMessage());
                    }
                });
            }
        }
    }
}

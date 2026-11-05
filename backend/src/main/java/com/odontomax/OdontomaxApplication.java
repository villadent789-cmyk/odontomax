package com.odontomax;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OdontomaxApplication {

    public static void main(String[] args) {
        cargarEnvSiExiste();
        SpringApplication.run(OdontomaxApplication.class, args);
    }

    private static void cargarEnvSiExiste() {
        File[] posibles = new File[] {
                new File(".env"),
                new File("backend/.env"),
                new File("../.env")
        };
        for (File f : posibles) {
            if (f.exists() && f.isFile()) {
                try {
                    List<String> lineas = Files.readAllLines(f.toPath());
                    for (String linea : lineas) {
                        String trimmed = linea.trim();
                        if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                            continue;
                        }
                        int sep = trimmed.indexOf('=');
                        String key = trimmed.substring(0, sep).trim();
                        String val = trimmed.substring(sep + 1).trim();
                        if (System.getProperty(key) == null && System.getenv(key) == null) {
                            System.setProperty(key, val);
                        }
                    }
                    break;
                } catch (Exception ignored) {
                }
            }
        }
    }
}

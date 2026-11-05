package com.odontomax.application.service;

import com.odontomax.application.dto.response.DatabaseSizeResponseDTO;
import com.odontomax.application.dto.response.TablaSizeDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MantenimientoService {

    private final JdbcTemplate jdbcTemplate;

    @Value("${clinica.database.quota-mb:500.0}")
    private double cuotaMb;

    public DatabaseSizeResponseDTO obtenerTamanoBaseDatos() {
        long totalBytes;
        long totalPublicBytes;
        List<TablaSizeDTO> tablas = new ArrayList<>();

        try {
            Long dbBytes = jdbcTemplate.queryForObject("SELECT pg_database_size(current_database())", Long.class);
            totalBytes = (dbBytes != null) ? dbBytes : 0L;

            String sqlPublicTotal = """
                SELECT COALESCE(SUM(pg_total_relation_size(c.oid)), 0)
                FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = 'public' AND c.relkind = 'r'
            """;
            Long pubBytes = jdbcTemplate.queryForObject(sqlPublicTotal, Long.class);
            totalPublicBytes = (pubBytes != null) ? pubBytes : 0L;

            String sqlTablas = """
                SELECT relname AS tabla,
                       pg_total_relation_size(c.oid) AS bytes
                FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = 'public' AND c.relkind = 'r'
                ORDER BY bytes DESC
                LIMIT 10
            """;

            tablas = jdbcTemplate.query(sqlTablas, (rs, rowNum) -> {
                long tBytes = rs.getLong("bytes");
                double tMb = Math.round((tBytes / (1024.0 * 1024.0)) * 100.0) / 100.0;
                return new TablaSizeDTO(rs.getString("tabla"), tBytes, tMb);
            });
        } catch (Exception e) {
            log.error("Fallo al consultar telemetría de almacenamiento en PostgreSQL", e);
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "El servicio de telemetría de base de datos no está disponible. No se pudo calcular el tamaño del almacenamiento.",
                    e
            );
        }

        double tamanoMb = Math.round((totalBytes / (1024.0 * 1024.0)) * 100.0) / 100.0;
        double tamanoPublicMb = Math.round((totalPublicBytes / (1024.0 * 1024.0)) * 100.0) / 100.0;
        double cuotaEfectiva = cuotaMb > 0 ? cuotaMb : 500.0;
        double porcentajeUso = Math.round((tamanoMb / cuotaEfectiva * 100.0) * 100.0) / 100.0;

        String nivelAlerta = "NORMAL";
        String mensaje = "Uso de almacenamiento dentro de los rangos normales de operación (" + porcentajeUso + "% de la cuota).";

        if (porcentajeUso >= 85.0) {
            nivelAlerta = "CRITICA";
            mensaje = "ALERTA CRÍTICA: La base de datos supera el 85% (" + porcentajeUso + "%) de la cuota asignada. Ejecute de inmediato el proceso de archivado manual.";
        } else if (porcentajeUso >= 70.0) {
            nivelAlerta = "ADVERTENCIA";
            mensaje = "ADVERTENCIA: La base de datos supera el 70% (" + porcentajeUso + "%) de la cuota asignada. Programe el archivado de tablas no críticas.";
        }

        return DatabaseSizeResponseDTO.builder()
                .tamanoBytes(totalBytes)
                .tamanoMb(tamanoMb)
                .tamanoPublicBytes(totalPublicBytes)
                .tamanoPublicMb(tamanoPublicMb)
                .cuotaMaximaMb(cuotaEfectiva)
                .porcentajeUso(porcentajeUso)
                .alerta(nivelAlerta)
                .mensaje(mensaje)
                .topTablas(tablas)
                .build();
    }
}

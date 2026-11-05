package com.odontomax.pacientes.application.port.out;

import java.time.OffsetDateTime;

public interface ConsentimientoPdfPort {
    byte[] generarDocumentoPdf(
            String titulo,
            String pacienteNombre,
            String tipo,
            String contenido,
            String firmante,
            String documento,
            OffsetDateTime fecha
    );
}
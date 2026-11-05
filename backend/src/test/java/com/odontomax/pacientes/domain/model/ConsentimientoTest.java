package com.odontomax.pacientes.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ConsentimientoTest {

    @Test
    @DisplayName("esTratamientoDatos: identifica correctamente el tipo")
    void esTratamientoDatos() {
        Consentimiento c1 = Consentimiento.builder().tipo("TRATAMIENTO_DATOS").build();
        assertTrue(c1.esTratamientoDatos());

        Consentimiento c2 = Consentimiento.builder().tipo("tratamiento_datos").build();
        assertTrue(c2.esTratamientoDatos());

        Consentimiento c3 = Consentimiento.builder().tipo("USO_IMAGEN").build();
        assertFalse(c3.esTratamientoDatos());
    }

    @Test
    @DisplayName("firmar: normaliza tipo a mayusculas y recorta firmante y documento")
    void firmarNormaliza() {
        UUID pacienteId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        OffsetDateTime fecha = OffsetDateTime.now();

        Consentimiento c = Consentimiento.firmar(
                pacienteId,
                null,
                null,
                "  tratamiento_datos  ",
                "Titulo",
                "Contenido",
                "firmaKey",
                "pdfKey",
                "  Juan Perez  ",
                "  70809010  ",
                "Padre",
                "127.0.0.1",
                usuarioId,
                fecha
        );

        assertEquals("TRATAMIENTO_DATOS", c.getTipo());
        assertEquals("Juan Perez", c.getNombreFirmante());
        assertEquals("70809010", c.getDocumentoFirmante());
        assertEquals(pacienteId, c.getPacienteId());
        assertEquals("pdfKey", c.getArchivoKey());
        assertEquals(fecha, c.getFirmadoEn());
    }
}
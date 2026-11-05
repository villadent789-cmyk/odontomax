package com.odontomax.pacientes.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Consentimiento {

    private UUID id;
    private UUID pacienteId;
    private UUID tratamientoId;
    private String tipo;
    private UUID plantillaId;
    private String titulo;
    private String contenidoAceptado;
    private String firmaImagenKey;
    private String pdfKey;
    private String archivoKey;
    private String nombreFirmante;
    private String documentoFirmante;
    private String parentescoFirmante;
    private String ipFirmante;
    private UUID registradoPor;
    private OffsetDateTime firmadoEn;

    public boolean esTratamientoDatos() {
        return "TRATAMIENTO_DATOS".equalsIgnoreCase(this.tipo);
    }

    public static Consentimiento firmar(
            UUID pacienteId,
            UUID tratamientoId,
            UUID plantillaId,
            String tipo,
            String titulo,
            String contenidoAceptado,
            String firmaImagenKey,
            String pdfKey,
            String nombreFirmante,
            String documentoFirmante,
            String parentescoFirmante,
            String ipFirmante,
            UUID registradoPor,
            OffsetDateTime firmadoEn
    ) {
        return Consentimiento.builder()
                .id(UUID.randomUUID())
                .pacienteId(pacienteId)
                .tratamientoId(tratamientoId)
                .plantillaId(plantillaId)
                .tipo(tipo != null ? tipo.trim().toUpperCase() : null)
                .titulo(titulo)
                .contenidoAceptado(contenidoAceptado)
                .firmaImagenKey(firmaImagenKey)
                .pdfKey(pdfKey)
                .archivoKey(pdfKey)
                .nombreFirmante(nombreFirmante != null ? nombreFirmante.trim() : null)
                .documentoFirmante(documentoFirmante != null ? documentoFirmante.trim() : null)
                .parentescoFirmante(parentescoFirmante)
                .ipFirmante(ipFirmante)
                .registradoPor(registradoPor)
                .firmadoEn(firmadoEn != null ? firmadoEn : OffsetDateTime.now())
                .build();
    }
}
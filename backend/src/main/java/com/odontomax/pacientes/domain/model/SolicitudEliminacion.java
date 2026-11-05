package com.odontomax.pacientes.domain.model;

import com.odontomax.pacientes.domain.exception.SolicitudCorreccionNoPendienteException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Petición de eliminar una radiografía o un paciente. Guarda qué se quiere eliminar (con una descripción legible, porque
 * el objetivo desaparece al aprobarse) y el motivo; solo el dueño la resuelve.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SolicitudEliminacion {

    public static final String RADIOGRAFIA = "RADIOGRAFIA";
    public static final String PACIENTE = "PACIENTE";

    public static final String PENDIENTE = "PENDIENTE";
    public static final String APROBADA = "APROBADA";
    public static final String RECHAZADA = "RECHAZADA";

    private UUID id;
    private String tipo;
    private UUID objetivoId;
    private UUID pacienteId;
    private String sedeId;
    private String descripcion;
    private String motivo;
    private String estado;
    private UUID solicitadoPor;
    private String solicitadoPorNombre;
    private OffsetDateTime solicitadoEn;
    private UUID resueltoPor;
    private String resueltoPorNombre;
    private OffsetDateTime resueltoEn;
    private String comentarioResolucion;

    public static SolicitudEliminacion nueva(String tipo, UUID objetivoId, UUID pacienteId, String sedeId, String descripcion,
                                             String motivo, UUID solicitadoPor, String solicitadoPorNombre) {
        if (!RADIOGRAFIA.equals(tipo) && !PACIENTE.equals(tipo)) {
            throw new IllegalArgumentException("Solo se puede solicitar eliminar una radiografía o un paciente.");
        }
        if (motivo == null || motivo.trim().length() < 5) {
            throw new IllegalArgumentException("Explica el motivo de la eliminación (mínimo 5 caracteres).");
        }
        return SolicitudEliminacion.builder()
                .tipo(tipo).objetivoId(objetivoId).pacienteId(pacienteId).sedeId(sedeId).descripcion(descripcion)
                .motivo(motivo.trim()).estado(PENDIENTE)
                .solicitadoPor(solicitadoPor).solicitadoPorNombre(solicitadoPorNombre)
                .build();
    }

    public void aprobar(UUID usuarioId, String usuarioNombre, OffsetDateTime ahora, String comentario) {
        exigirPendiente();
        estado = APROBADA;
        resolver(usuarioId, usuarioNombre, ahora, comentario);
    }

    public void rechazar(UUID usuarioId, String usuarioNombre, OffsetDateTime ahora, String comentario) {
        exigirPendiente();
        estado = RECHAZADA;
        resolver(usuarioId, usuarioNombre, ahora, comentario);
    }

    private void resolver(UUID usuarioId, String usuarioNombre, OffsetDateTime ahora, String comentario) {
        resueltoPor = usuarioId;
        resueltoPorNombre = usuarioNombre;
        resueltoEn = ahora;
        comentarioResolucion = comentario == null || comentario.isBlank() ? null : comentario.trim();
    }

    private void exigirPendiente() {
        if (!PENDIENTE.equals(estado)) {
            throw new SolicitudCorreccionNoPendienteException("Esta solicitud ya fue " + estado.toLowerCase() + ".");
        }
    }
}

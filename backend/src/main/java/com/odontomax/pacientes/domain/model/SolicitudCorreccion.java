package com.odontomax.pacientes.domain.model;

import com.odontomax.pacientes.domain.exception.SolicitudCorreccionNoPendienteException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Petición de corregir el nombre, el DNI o la fecha de nacimiento de un paciente. Guarda lo que había (para que quede
 * constancia) y lo que se propone; solo el dueño la resuelve. Un valor propuesto nulo significa "sin cambio".
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SolicitudCorreccion {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String APROBADA = "APROBADA";
    public static final String RECHAZADA = "RECHAZADA";

    private UUID id;
    private UUID pacienteId;
    private String sedeId;
    private String nombresAnterior;
    private String apellidosAnterior;
    private String dniAnterior;
    private LocalDate fechaNacimientoAnterior;
    private String nombresNuevo;
    private String apellidosNuevo;
    private String dniNuevo;
    private LocalDate fechaNacimientoNueva;
    private String motivo;
    private String estado;
    private UUID solicitadoPor;
    private String solicitadoPorNombre;
    private OffsetDateTime solicitadoEn;
    private UUID resueltoPor;
    private String resueltoPorNombre;
    private OffsetDateTime resueltoEn;
    private String comentarioResolucion;

    /** Una solicitud nueva debe explicar el motivo y cambiar al menos un dato respecto a lo vigente. */
    public static SolicitudCorreccion nueva(UUID pacienteId, String sedeId, String nombres, String apellidos, String dni,
                                            LocalDate fechaNacimiento, String nombresNuevo, String apellidosNuevo,
                                            String dniNuevo, LocalDate fechaNueva, String motivo,
                                            UUID solicitadoPor, String solicitadoPorNombre) {
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("Explica el motivo de la corrección.");
        }
        String n = distinto(nombresNuevo, nombres);
        String a = distinto(apellidosNuevo, apellidos);
        String d = distinto(dniNuevo, dni);
        LocalDate f = fechaNueva != null && !fechaNueva.equals(fechaNacimiento) ? fechaNueva : null;
        if (n == null && a == null && d == null && f == null) {
            throw new IllegalArgumentException("No hay ningún cambio respecto a los datos actuales del paciente.");
        }
        return SolicitudCorreccion.builder()
                .pacienteId(pacienteId).sedeId(sedeId)
                .nombresAnterior(nombres).apellidosAnterior(apellidos).dniAnterior(dni).fechaNacimientoAnterior(fechaNacimiento)
                .nombresNuevo(n).apellidosNuevo(a).dniNuevo(d).fechaNacimientoNueva(f)
                .motivo(motivo.trim()).estado(PENDIENTE)
                .solicitadoPor(solicitadoPor).solicitadoPorNombre(solicitadoPorNombre)
                .build();
    }

    private static String distinto(String propuesto, String actual) {
        if (propuesto == null || propuesto.isBlank()) {
            return null;
        }
        String limpio = propuesto.trim();
        return limpio.equals(actual) ? null : limpio;
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

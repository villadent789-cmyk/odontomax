package com.odontomax.pacientes.application.port.out;

import java.util.Optional;
import java.util.UUID;

/** Qué se quiere eliminar y cómo se elimina, sin que este módulo conozca a quien guarda las radiografías. */
public interface EliminacionObjetivosPort {

    /** Lo necesario para describir y autorizar una solicitud: de qué paciente y sede es y cómo se ve en pantalla. */
    record Objetivo(UUID pacienteId, String sedeId, String descripcion) {
    }

    Optional<Objetivo> radiografia(UUID radiografiaId);

    /** Elimina la radiografía (fila y archivo); el motivo queda en la auditoría. */
    void eliminarRadiografia(UUID radiografiaId, String motivo);
}

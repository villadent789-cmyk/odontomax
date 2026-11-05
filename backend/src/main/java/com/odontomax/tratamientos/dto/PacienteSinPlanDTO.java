package com.odontomax.tratamientos.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

/** Un paciente con hallazgos por tratar en el odontograma y sin plan ni presupuesto en marcha. */
@Getter
@Builder
public class PacienteSinPlanDTO {
    private final UUID pacienteId;
    private final String pacienteNombre;
    private final String telefono;
    /** Cuántos hallazgos por tratar tiene vigentes (una pieza con dos hallazgos cuenta dos). */
    private final int hallazgos;
    /** Identificadores de los hallazgos por tratar, sin repetir (p. ej. caries, fractura). */
    private final List<String> tipos;
}

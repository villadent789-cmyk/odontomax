package com.odontomax.pacientes.domain.model;

/**
 * Cantidad de registros del paciente que impiden su eliminación física: el expediente solo puede archivarse.
 * Las citas no protegen.
 */
public record RegistrosProtegidos(
        long notasEvolucion,
        long antecedentes,
        long accesos,
        long pagos,
        long consentimientos,
        long ordenesLaboratorio
) {
}

package com.odontomax.pacientes.application.port.in;

import com.odontomax.pacientes.application.dto.response.DescuentoRecomendacionResponseDTO;

import java.util.List;
import java.util.UUID;

/** Descuentos por recomendación: avisan al doctor que el paciente trajo a alguien; el monto lo decide el doctor. */
public interface GestionarDescuentosRecomendacionUseCase {

    /** Los del paciente que recomendó, los pendientes primero. */
    List<DescuentoRecomendacionResponseDTO> listar(UUID pacienteId);

    /** Marca el descuento como dado (en el plan indicado). */
    DescuentoRecomendacionResponseDTO aplicar(UUID pacienteId, UUID descuentoId, UUID tratamientoId);

    /** Al registrar un paciente nuevo recomendado por otro, deja pendiente el descuento de quien lo recomendó. */
    void registrarRecomendacion(UUID pacienteRecomendadorId, UUID pacienteNuevoId);
}

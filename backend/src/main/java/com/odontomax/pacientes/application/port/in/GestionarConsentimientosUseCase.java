package com.odontomax.pacientes.application.port.in;

import com.odontomax.pacientes.application.dto.request.ConsentimientoRequestDTO;
import com.odontomax.pacientes.application.dto.response.ConsentimientoResponseDTO;
import com.odontomax.pacientes.application.dto.response.PlantillaConsentimientoResponseDTO;

import java.util.List;
import java.util.UUID;

public interface GestionarConsentimientosUseCase {
    List<PlantillaConsentimientoResponseDTO> listarPlantillas();
    List<ConsentimientoResponseDTO> listarPorPaciente(UUID pacienteId);
    ConsentimientoResponseDTO registrarConsentimiento(ConsentimientoRequestDTO dto);
    default byte[] obtenerFirmaImagen(UUID consentimientoId) {
        return new byte[0];
    }
    default byte[] obtenerPdf(UUID consentimientoId) {
        return new byte[0];
    }
}
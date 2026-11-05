package com.odontomax.agenda.application.port.in;

import com.odontomax.agenda.application.dto.request.CitaCancelarRequestDTO;
import com.odontomax.agenda.application.dto.response.CitaResponseDTO;

import java.util.UUID;

public interface CancelarCitaUseCase {
    CitaResponseDTO cancelarCita(UUID citaId, CitaCancelarRequestDTO request);
}

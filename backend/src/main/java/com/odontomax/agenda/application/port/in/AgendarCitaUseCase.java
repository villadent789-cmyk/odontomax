package com.odontomax.agenda.application.port.in;

import com.odontomax.agenda.application.dto.request.CitaRequestDTO;
import com.odontomax.agenda.application.dto.response.CitaResponseDTO;

public interface AgendarCitaUseCase {
    CitaResponseDTO agendarCita(CitaRequestDTO request, String sedeEfectiva);
}

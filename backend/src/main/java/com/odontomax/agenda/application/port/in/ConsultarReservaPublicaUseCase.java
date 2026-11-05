package com.odontomax.agenda.application.port.in;

import com.odontomax.agenda.application.dto.response.ConsultaCitaResponseDTO;

public interface ConsultarReservaPublicaUseCase {

    ConsultaCitaResponseDTO consultarReservaPublica(String ticket, String dni);
}

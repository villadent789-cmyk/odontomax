package com.odontomax.agenda.application.port.in;

import com.odontomax.agenda.application.dto.request.ReservaPublicaRequestDTO;
import com.odontomax.agenda.application.dto.response.TicketReservaDTO;

public interface RegistrarReservaPublicaUseCase {

    TicketReservaDTO registrarReservaPublica(ReservaPublicaRequestDTO dto);
}

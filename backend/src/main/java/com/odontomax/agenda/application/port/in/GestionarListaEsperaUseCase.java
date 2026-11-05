package com.odontomax.agenda.application.port.in;

import com.odontomax.agenda.application.dto.request.EsperaHuecoRequestDTO;
import com.odontomax.agenda.application.dto.response.EsperaHuecoResponseDTO;

import java.util.List;
import java.util.UUID;

public interface GestionarListaEsperaUseCase {

    EsperaHuecoResponseDTO agregar(EsperaHuecoRequestDTO request);

    List<EsperaHuecoResponseDTO> listar(String sedeId);

    EsperaHuecoResponseDTO cambiarEstado(UUID id, String estado);
}

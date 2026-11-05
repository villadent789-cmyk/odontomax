package com.odontomax.pacientes.application.port.in;

import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.pacientes.application.dto.response.HistoriaClinicaAccesoResponseDTO;

import java.util.List;
import java.util.UUID;

public interface GestionarAccesoHistoriaUseCase {
    HistoriaClinicaAccesoResponseDTO registrarApertura(UUID pacienteId);
    void registrarLecturaDeduplicada(UUID pacienteId, ClinicaUserPrincipal user, String ip);
    List<HistoriaClinicaAccesoResponseDTO> listarAccesos(UUID pacienteId);
}
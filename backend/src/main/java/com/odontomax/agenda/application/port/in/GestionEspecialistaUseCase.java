package com.odontomax.agenda.application.port.in;

import com.odontomax.agenda.application.dto.request.EspecialistaRequestDTO;
import com.odontomax.agenda.application.dto.request.MetaDoctorRequestDTO;
import com.odontomax.agenda.application.dto.response.MetaDoctorResponseDTO;
import com.odontomax.agenda.application.dto.request.SillonesEspecialistaRequestDTO;
import com.odontomax.agenda.application.dto.response.EspecialistaResponseDTO;

import java.util.UUID;

public interface GestionEspecialistaUseCase {
    EspecialistaResponseDTO crear(EspecialistaRequestDTO dto);
    EspecialistaResponseDTO actualizar(UUID id, EspecialistaRequestDTO dto);
    EspecialistaResponseDTO actualizarSillones(UUID id, SillonesEspecialistaRequestDTO dto);
    MetaDoctorResponseDTO obtenerMeta(UUID id);
    MetaDoctorResponseDTO actualizarMeta(UUID id, MetaDoctorRequestDTO dto);
    EspecialistaResponseDTO cambiarEstado(UUID id, boolean activo);
    void eliminar(UUID id);
    EspecialistaResponseDTO actualizarMiEspecialidad(UUID usuarioId, String especialidad);
}

package com.odontomax.identidad.api;

import java.util.Optional;
import java.util.UUID;

/** Consulta de usuarios para otros módulos, sin exponer la persistencia de identidad. */
public interface ConsultaUsuarioModuloApi {

    Optional<UsuarioModuloDTO> buscarPorId(UUID usuarioId);
}

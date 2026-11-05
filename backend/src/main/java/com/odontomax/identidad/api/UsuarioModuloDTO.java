package com.odontomax.identidad.api;

import java.util.UUID;

/** Datos públicos de un usuario para otros módulos (nunca credenciales ni secretos). */
public record UsuarioModuloDTO(
        UUID id,
        String nombre,
        String email,
        String rol,
        String sedeId,
        boolean activo
) {
}

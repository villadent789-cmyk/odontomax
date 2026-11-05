package com.odontomax.identidad.api.event;

import java.util.UUID;

public record UsuarioEstadoModificado(
        UUID usuarioId,
        boolean activo
) {
}

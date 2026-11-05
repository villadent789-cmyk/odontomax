package com.odontomax.identidad.api.event;

import java.util.UUID;

public record UsuarioOdontologoRegistrado(
        UUID usuarioId,
        String nombre,
        String email,
        String sedeId,
        String especialidad
) {
    public UsuarioOdontologoRegistrado(UUID usuarioId, String nombre, String email, String sedeId) {
        this(usuarioId, nombre, email, sedeId, null);
    }
}

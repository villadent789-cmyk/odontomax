package com.odontomax.agenda.application.port.out;

import java.util.Optional;
import java.util.UUID;

public interface UsuarioConsultaPort {
    Optional<UsuarioInfo> findById(UUID usuarioId);

    record UsuarioInfo(UUID id, String nombre, String email, String rol, String sedeId, boolean activo,
                       java.util.List<String> rolesAdicionales) {
        public UsuarioInfo(UUID id, String nombre, String email, String rol, String sedeId, boolean activo) {
            this(id, nombre, email, rol, sedeId, activo, java.util.List.of());
        }
    }
}

package com.odontomax.agenda.infrastructure.adapter;

import com.odontomax.agenda.application.port.out.UsuarioConsultaPort;
import com.odontomax.identidad.api.ConsultaUsuarioModuloApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UsuarioCrossModuleAdapter implements UsuarioConsultaPort {

    private final ConsultaUsuarioModuloApi consultaUsuarioModuloApi;

    @Override
    public Optional<UsuarioInfo> findById(UUID usuarioId) {
        if (usuarioId == null) {
            return Optional.empty();
        }
        return consultaUsuarioModuloApi.buscarPorId(usuarioId)
                .map(u -> new UsuarioInfo(u.id(), u.nombre(), u.email(), u.rol(), u.sedeId(), u.activo()));
    }
}

package com.odontomax.identidad.application.service;

import com.odontomax.identidad.api.ConsultaUsuarioModuloApi;
import com.odontomax.identidad.api.UsuarioModuloDTO;
import com.odontomax.identidad.application.port.out.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConsultaUsuarioModuloService implements ConsultaUsuarioModuloApi {

    private final UsuarioRepository usuarioRepository;

    @Override
    public Optional<UsuarioModuloDTO> buscarPorId(UUID usuarioId) {
        return usuarioRepository.findById(usuarioId)
                .map(u -> new UsuarioModuloDTO(u.getId(), u.getNombre(), u.getEmail(), u.getRol(), u.getSedeId(), u.isActivo(), u.getRolesAdicionales()));
    }
}

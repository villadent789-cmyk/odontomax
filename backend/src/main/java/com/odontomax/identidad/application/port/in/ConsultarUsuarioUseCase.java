package com.odontomax.identidad.application.port.in;

import com.odontomax.application.dto.response.UsuarioResponseDTO;

import java.util.List;

public interface ConsultarUsuarioUseCase {

    /** Todas las cuentas, ordenadas por nombre sin distinguir mayúsculas. */
    List<UsuarioResponseDTO> listarTodos();
}

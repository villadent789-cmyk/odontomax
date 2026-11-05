package com.odontomax.identidad.application.port.in;

import com.odontomax.application.dto.response.UsuarioDTO;

public interface ConsultarPerfilUseCase {

    /** Perfil del usuario activo dueño del JWT (acepta el valor con o sin prefijo "Bearer "). */
    UsuarioDTO getPerfilFromToken(String bearerToken);
}

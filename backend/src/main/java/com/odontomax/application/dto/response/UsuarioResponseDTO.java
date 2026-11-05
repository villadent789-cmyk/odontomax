package com.odontomax.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioResponseDTO {

    private UUID id;
    private String nombre;
    private String email;
    private String rol;
    private String sedeId;
    private String sedeNombre;
    private boolean activo;
    private OffsetDateTime ultimoAcceso;
    private OffsetDateTime creadoEn;
}

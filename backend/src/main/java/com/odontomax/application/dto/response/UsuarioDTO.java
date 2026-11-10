package com.odontomax.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioDTO {
    private UUID id;
    private String email;
    private String nombre;
    private String rol;
    /** Funciones adicionales al cargo principal; sus permisos se suman. Se omite si no tiene. */
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY)
    private java.util.List<String> rolesAdicionales;
    private String sedeId;
}

package com.odontomax.application.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioCreateRequestDTO {

    @NotBlank(message = "El nombre completo es obligatorio")
    @Size(min = 3, max = 150, message = "El nombre debe tener entre 3 y 150 caracteres")
    private String nombre;

    @NotBlank(message = "El correo electrónico es obligatorio")
    @Email(message = "Ingrese un correo electrónico válido")
    private String email;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 4, max = 128, message = "La contraseña debe tener al menos 4 caracteres")
    private String password;

    @NotBlank(message = "El rol es obligatorio")
    @Pattern(regexp = "^(ADMIN_GENERAL|ADMIN_SEDE|ODONTOLOGO|RECEPCION)$",
            message = "Rol inválido. Roles permitidos: ADMIN_GENERAL, ADMIN_SEDE, ODONTOLOGO, RECEPCION")
    private String rol;

    /** Funciones adicionales al cargo principal (ODONTOLOGO y/o RECEPCION). Sus permisos se suman. */
    private java.util.List<@Pattern(regexp = "^(ODONTOLOGO|RECEPCION)$", message = "Función adicional inválida") String> rolesAdicionales;

    @Pattern(regexp = "^sede-[a-z0-9-]{1,60}$",
            message = "Sede inválida")
    private String sedeId;

    @Size(max = 80, message = "La especialidad no puede superar los 80 caracteres")
    private String especialidad;
}

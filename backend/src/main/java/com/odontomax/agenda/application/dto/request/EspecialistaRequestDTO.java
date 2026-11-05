package com.odontomax.agenda.application.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EspecialistaRequestDTO {

    @NotBlank(message = "El nombre del especialista es obligatorio")
    @Size(max = 120, message = "El nombre no puede superar los 120 caracteres")
    private String nombre;

    @NotBlank(message = "La especialidad es obligatoria")
    @Size(max = 80, message = "La especialidad no puede superar los 80 caracteres")
    private String especialidad;

    @Size(max = 20, message = "El COP no puede superar los 20 caracteres")
    private String cop;

    @Email(message = "Ingrese un correo electrónico válido")
    @Size(max = 120, message = "El correo no puede superar los 120 caracteres")
    private String email;

    @Pattern(regexp = "^(9\\d{8})?$", message = "El celular debe tener 9 dígitos y empezar con 9")
    private String telefono;

    @Pattern(regexp = "^(https://\\S+)?$", message = "La foto debe ser una URL que empiece con https://")
    @Size(max = 500, message = "La URL de la foto no puede superar los 500 caracteres")
    private String fotoUrl;

    @NotEmpty(message = "Debe asignar al menos una sede de atención")
    private List<String> sedes;

    private UUID usuarioId;
}

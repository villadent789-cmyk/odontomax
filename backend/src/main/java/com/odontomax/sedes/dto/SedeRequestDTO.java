package com.odontomax.sedes.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SedeRequestDTO {

    @NotBlank(message = "El nombre de la sede es obligatorio")
    @Size(max = 120, message = "El nombre no puede superar los 120 caracteres")
    private String nombre;

    @NotBlank(message = "La dirección de la sede es obligatoria")
    @Size(max = 250, message = "La dirección no puede superar los 250 caracteres")
    private String direccion;

    @Size(max = 120, message = "La ciudad no puede superar los 120 caracteres")
    private String ciudad;

    @Size(max = 30, message = "El teléfono no puede superar los 30 caracteres")
    private String telefono;

    @Size(max = 30, message = "El WhatsApp no puede superar los 30 caracteres")
    private String whatsapp;

    @Email(message = "El correo de la sede no tiene un formato válido")
    @Size(max = 120, message = "El correo no puede superar los 120 caracteres")
    private String email;

    @Size(max = 200, message = "El horario no puede superar los 200 caracteres")
    private String horarioTexto;

    // La columna es smallint; el límite superior evita un error de base de datos (500)
    @NotNull(message = "La cantidad de sillones es obligatoria")
    @Min(value = 1, message = "La sede debe tener al menos 1 sillón")
    @Max(value = 99, message = "La cantidad de sillones no puede superar 99")
    private Integer sillones;

    @Size(max = 1000, message = "La descripción no puede superar los 1000 caracteres")
    private String descripcion;

    @Size(max = 20, message = "El color no puede superar los 20 caracteres")
    private String color;

    @Size(max = 20, message = "El color secundario no puede superar los 20 caracteres")
    private String colorSecundario;

    private com.odontomax.sedes.model.UnidadDeNegocio unidadDeNegocio;

    @Size(max = 11, message = "El RUC no puede superar los 11 dígitos")
    private String ruc;
}

package com.odontomax.tratamientos.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** Plantilla de plan: sirve de petición al crearla y de respuesta al listarla ({@code id} solo en la respuesta). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlantillaPlanDTO {

    private UUID id;

    @NotBlank(message = "El nombre de la plantilla es obligatorio")
    @Size(max = 120, message = "El nombre no puede pasar de 120 caracteres")
    private String nombre;

    @NotBlank(message = "El servicio es obligatorio")
    @Size(max = 60)
    private String procedimientoId;

    @NotBlank(message = "El nombre del servicio es obligatorio")
    @Size(max = 200)
    private String servicioNombre;

    @Min(value = 1, message = "Indica al menos una pieza")
    @Max(value = 32, message = "Máximo 32 piezas")
    private int piezas;

    @Pattern(regexp = "LEVE|MODERADO|SEVERO", message = "La gravedad debe ser LEVE, MODERADO o SEVERO")
    private String gravedad;

    @Min(value = 1, message = "Indica al menos una sesión")
    @Max(value = 60, message = "Máximo 60 sesiones")
    private int sesiones;

    @Size(max = 300)
    private String notas;
}

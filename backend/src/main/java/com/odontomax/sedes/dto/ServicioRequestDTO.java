package com.odontomax.sedes.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServicioRequestDTO {

    @NotBlank(message = "El nombre del servicio es obligatorio")
    @Size(max = 120, message = "El nombre no puede superar los 120 caracteres")
    private String nombre;

    @NotBlank(message = "La especialidad del servicio es obligatoria")
    @Size(max = 80, message = "La especialidad no puede superar los 80 caracteres")
    private String especialidad;

    @Size(max = 1000, message = "La descripción no puede superar los 1000 caracteres")
    private String descripcion;

    @NotNull(message = "La duración es obligatoria")
    @Min(value = 5, message = "La duración mínima es 5 minutos")
    @Max(value = 480, message = "La duración máxima es 480 minutos")
    private Integer duracionMin;

    /** Escala de la columna {@code precio_desde numeric(10,2)}: 8 enteros y 2 decimales (B-025). */
    @DecimalMin(value = "0.00", message = "El precio no puede ser negativo")
    @Digits(integer = 8, fraction = 2, message = "El precio admite como máximo 8 dígitos enteros y 2 decimales")
    private BigDecimal precio;

    /** Rango de la columna {@code orden smallint} (B-026); nulo = 0 en el alta y sin cambio en el PUT. */
    @Min(value = -32768, message = "El orden debe estar entre -32768 y 32767")
    @Max(value = 32767, message = "El orden debe estar entre -32768 y 32767")
    private Integer orden;

    /** Porcentaje para el doctor que deriva (estudios de Villamax); nulo en el PUT conserva el valor actual. */
    @DecimalMin(value = "0.00", message = "La comisión no puede ser negativa")
    @DecimalMax(value = "100.00", message = "La comisión no puede superar el 100 %")
    private BigDecimal comisionPorcentaje;

    /** Monto fijo por estudio para el doctor externo (estudios de Villamax); nulo en el PUT conserva el valor actual. */
    @DecimalMin(value = "0.00", message = "La comisión fija no puede ser negativa")
    @Digits(integer = 8, fraction = 2, message = "La comisión fija admite como máximo 8 dígitos enteros y 2 decimales")
    private BigDecimal comisionFija;

    /** Precio por pieza según la gravedad del caso (opcionales); nulo en el PUT conserva el valor actual. */
    @DecimalMin(value = "0.00", message = "El precio leve no puede ser negativo")
    @Digits(integer = 8, fraction = 2, message = "El precio leve admite como máximo 8 dígitos enteros y 2 decimales")
    private BigDecimal precioLeve;

    @DecimalMin(value = "0.00", message = "El precio moderado no puede ser negativo")
    @Digits(integer = 8, fraction = 2, message = "El precio moderado admite como máximo 8 dígitos enteros y 2 decimales")
    private BigDecimal precioModerado;

    @DecimalMin(value = "0.00", message = "El precio severo no puede ser negativo")
    @Digits(integer = 8, fraction = 2, message = "El precio severo admite como máximo 8 dígitos enteros y 2 decimales")
    private BigDecimal precioSevero;
}

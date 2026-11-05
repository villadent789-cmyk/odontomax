package com.odontomax.application.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TratamientoCreateRequestDTO {

    @NotNull(message = "El pacienteId es obligatorio")
    private UUID pacienteId;

    @NotBlank(message = "La sedeId es obligatoria")
    private String sedeId;

    @NotNull(message = "El especialistaId es obligatorio")
    private UUID especialistaId;

    private String procedimientoId;

    @NotBlank(message = "El nombre del plan o tratamiento es obligatorio")
    private String nombre;

    private String piezaDental;

    private String diagnostico;

    @Builder.Default
    private String modalidadPago = "CONTADO";

    @NotNull(message = "El costo total es obligatorio")
    @DecimalMin(value = "0.00", message = "El costo total no puede ser negativo")
    private BigDecimal costoTotal;

    @Builder.Default
    private BigDecimal descuentoTotal = BigDecimal.ZERO;

    private LocalDate fechaInicio;

    private LocalDate fechaFinEstimada;

    @Builder.Default
    private Integer totalSesiones = 1;

    private String observaciones;

    /** Plan en cuotas (ortodoncia). Si viene, el costo del plan es la suma de las cuotas y la modalidad pasa a CUOTAS. */
    @Valid
    private CuotasPlanRequestDTO cuotas;

    /** Precio del catálogo de referencia. Si el costo acordado difiere, el motivo es obligatorio. */
    @DecimalMin(value = "0.00", message = "El precio de catálogo no puede ser negativo")
    private BigDecimal precioCatalogo;

    private String motivoAjustePrecio;

    /** Monto ya cancelado antes de ingresar al sistema (regularización histórica, ej. ortodoncia). No genera movimiento en caja. */
    @Builder.Default
    @DecimalMin(value = "0.00", message = "El monto pagado antes del sistema no puede ser negativo")
    private BigDecimal pagadoAntesSistema = BigDecimal.ZERO;

    @Builder.Default
    @Valid
    private List<TratamientoItemRequestDTO> items = new ArrayList<>();
}

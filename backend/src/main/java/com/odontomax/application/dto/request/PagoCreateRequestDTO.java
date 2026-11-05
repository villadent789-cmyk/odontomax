package com.odontomax.application.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PagoCreateRequestDTO {

    @NotNull(message = "El pacienteId es obligatorio")
    private UUID pacienteId;

    private UUID tratamientoId;

    @NotBlank(message = "La sedeId es obligatoria")
    private String sedeId;

    @NotNull(message = "El monto es obligatorio")
    @DecimalMin(value = "0.01", message = "El monto debe ser mayor a cero")
    private BigDecimal monto;

    @NotBlank(message = "El método de pago es obligatorio")
    @Pattern(regexp = "EFECTIVO|YAPE|PLIN|TARJETA_DEBITO|TARJETA_CREDITO", message = "El método de pago debe ser EFECTIVO, YAPE, PLIN, TARJETA_DEBITO o TARJETA_CREDITO")
    private String metodoPago;

    private String numeroOperacion;

    @Builder.Default
    private String comprobanteTipo = "RECIBO"; // "RECIBO", "BOLETA", "FACTURA"

    private UUID cajaSesionId;

    /** Doctor que atendió. Un odontólogo que cobra queda como atendido por sí mismo; recepción lo elige o se toma del tratamiento. */
    private UUID especialistaId;
}

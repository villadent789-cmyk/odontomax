package com.odontomax.promociones.application.dto;

import com.odontomax.promociones.domain.model.TipoPromocion;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfiguracionPromocionRequestDTO {

    @NotBlank(message = "La sede es obligatoria")
    private String sedeId;

    @NotNull(message = "El tipo de promoción es obligatorio")
    private TipoPromocion tipo;

    @DecimalMin(value = "0.0", inclusive = true, message = "El valor debe ser mayor o igual a 0")
    private BigDecimal valor;

    @NotBlank(message = "La descripción del beneficio es obligatoria")
    private String descripcionBeneficio;

    @NotBlank(message = "La plantilla del mensaje es obligatoria")
    private String plantillaMensaje;

    @Min(value = 1, message = "Los días de vigencia deben ser al menos 1")
    private int diasVigencia;

    private boolean activa;
}

package com.odontomax.tratamientos.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresupuestoAceptarRequestDTO {

    /** Líneas que el paciente aceptó; vacío o nulo = todas. */
    private List<UUID> itemIds;

    /** Doctor responsable del plan; nulo = el del presupuesto. */
    private UUID especialistaId;

    @Min(value = 1, message = "El plan necesita al menos una sesión")
    @Max(value = 60, message = "Un plan no puede tener más de 60 sesiones")
    private Integer totalSesiones;
}

package com.odontomax.application.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OdontogramaLoteRequestDTO {

    @NotEmpty(message = "Debe proporcionar al menos una pieza dental en el lote.")
    @Valid
    private List<OdontogramaPiezaRequestDTO> piezas;
}

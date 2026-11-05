package com.odontomax.agenda.application.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SillonesEspecialistaRequestDTO {
    @NotNull
    private Map<String, Integer> sillonesPorSede;

    @NotNull
    @Builder.Default
    private List<String> sedesSinSillon = new ArrayList<>();
}

package com.odontomax.agenda.application.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EspecialistaResponseDTO {
    private UUID id;
    private UUID usuarioId;
    private String nombre;
    private String especialidad;
    private String cop;
    private String email;
    private String telefono;
    private String fotoUrl;
    private boolean activo;
    private List<String> sedes;
    private Map<String, Integer> sillonesPorSede;
}

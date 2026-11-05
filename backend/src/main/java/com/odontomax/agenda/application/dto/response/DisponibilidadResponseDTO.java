package com.odontomax.agenda.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisponibilidadResponseDTO {
    private UUID especialistaId;
    private String especialistaNombre;
    private LocalDate fecha;
    private String sedeId;
    private boolean atiendeEseDia;
    private String mensaje;
    private List<DisponibilidadSlotDTO> slots;
}

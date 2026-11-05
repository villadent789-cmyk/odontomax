package com.odontomax.agenda.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BandejaTareasResponseDTO {

    private LocalDate fecha;
    private String sedeId;
    private String sedeNombre;
    private String telefonoWhatsappSede;

    private int totalPendientes;
    private int totalLlamadasPendientes;
    private int totalRecordatoriosPendientes;
    private int totalCumpleanosPendientes;
    private int totalAdelantosPorExpirar;

    private List<ItemTareaBandejaDTO> tareas;
}

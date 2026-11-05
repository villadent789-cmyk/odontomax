package com.odontomax.agenda.application.dto.response;

import com.odontomax.agenda.domain.model.EsperaHueco;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EsperaHuecoResponseDTO {
    private UUID id;
    private String sedeId;
    private UUID pacienteId;
    private String pacienteNombre;
    private String pacienteTelefono;
    private UUID especialistaId;
    private String motivo;
    private String notas;
    /** ACTIVA, OFRECIDA, AGENDADA o CANCELADA. */
    private String estado;
    private Instant creadoEn;

    public static EsperaHuecoResponseDTO fromDomain(EsperaHueco e) {
        return EsperaHuecoResponseDTO.builder()
                .id(e.getId())
                .sedeId(e.getSedeId())
                .pacienteId(e.getPacienteId())
                .pacienteNombre(e.getPacienteNombre())
                .pacienteTelefono(e.getPacienteTelefono())
                .especialistaId(e.getEspecialistaId())
                .motivo(e.getMotivo())
                .notas(e.getNotas())
                .estado(e.getEstado())
                .creadoEn(e.getCreadoEn())
                .build();
    }
}
